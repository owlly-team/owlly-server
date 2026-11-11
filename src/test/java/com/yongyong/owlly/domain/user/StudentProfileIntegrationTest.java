package com.yongyong.owlly.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.*;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.journey.entity.*;
import com.yongyong.owlly.domain.journey.repository.*;
import com.yongyong.owlly.domain.region.entity.*;
import com.yongyong.owlly.domain.region.repository.*;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.*;
import com.yongyong.owlly.domain.user.service.StudentProfileReceiptCleanup;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL·암호화·보안 필터는 실제 구현, 외부 행정동 판별만 대체한다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false",
        "owlly.verification.secret=01234567890123456789012345678901", "owlly.profile.receipt-ttl-seconds=86400"})
@AutoConfigureMockMvc
class StudentProfileIntegrationTest {
    private static final String PATH = "/api/v1/me/profile";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired UniversityRepository universities;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired JourneyRepository journeys;
    @Autowired RideRequestRepository rides;
    @Autowired PickupPointRepository pickups;
    @Autowired StudentProfileReceiptCleanup cleanup;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean TextCryptor cryptor;
    @MockitoSpyBean StudentProfileUpdateReceiptRepository receipts;
    @MockitoBean DropoffLocationService locations;
    User student;
    User other;
    AdministrativeArea city;
    AdministrativeArea oldDong;
    AdministrativeArea newDong;
    final List<String> keys = new CopyOnWriteArrayList<>();
    Long journeyId;
    Long rideId;
    Long pickupId;

    @BeforeEach void setup() {
        Long universityId = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        city = area("프로필테스트시", AreaLevel.CITY, null);
        oldDong = area("이전동", AreaLevel.DONG, city.getId());
        newDong = area("새하차동", AreaLevel.DONG, city.getId());
        student = account(universityId);
        other = account(universityId);
        for (User user : List.of(student, other)) {
            profiles.saveAndFlush(StudentProfile.builder().userId(user.getId()).gender(Gender.FEMALE)
                    .realNameEncrypted(cryptor.encrypt("한서윤")).sameGenderDefault(true).defaultDongId(oldDong.getId())
                    .dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1")).build());
        }
        when(locations.resolveDong(anyDouble(), anyDouble())).thenReturn(newDong);
    }

    @AfterEach void deleteOnlyTestRecords() {
        for (String key : keys) jdbc.update("delete from student_profile_update_receipts where request_key=?", key);
        if (rideId != null) rides.deleteById(rideId);
        if (journeyId != null) journeys.deleteById(journeyId);
        if (pickupId != null) pickups.deleteById(pickupId);
        for (User user : List.of(student, other)) {
            String principal = user.getId().toString();
            jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name=?)", principal);
            jdbc.update("delete from spring_session where principal_name=?", principal);
            profiles.deleteById(user.getId());
            users.deleteById(user.getId());
        }
        areas.deleteById(oldDong.getId());
        areas.deleteById(newDong.getId());
        areas.deleteById(city.getId());
    }

    @Test void readsOnlyOwnDecryptedProfileWithNamesAndEtag() throws Exception {
        var result = getProfile(student).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(student.getId().toString()))
                .andExpect(jsonPath("$.data.user.universityName").value("한국외국어대학교"))
                .andExpect(jsonPath("$.data.realName").value("한서윤"))
                .andExpect(jsonPath("$.data.genderCode").value("FEMALE"))
                .andExpect(jsonPath("$.data.defaultDongId").value(oldDong.getId().toString()))
                .andExpect(jsonPath("$.data.defaultDongName").value("이전동"))
                .andExpect(jsonPath("$.data.defaultDropoff.latitude").value(37.3))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getHeader("ETag")).startsWith("\"").endsWith("\"");
        mvc.perform(get(PATH).param("userId", other.getId().toString()).with(authentication(auth(student))))
                .andExpect(jsonPath("$.data.user.id").value(student.getId().toString()));
        verifyNoInteractions(locations);
    }

    @Test void swaggerExposesProfileOperationsAndRequiredPatchHeaders() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = mapper.readTree(response.getResponse().getContentAsString());
        var endpoint = document.path("paths").path(PATH);
        assertThat(endpoint.path("get").path("operationId").asText()).isEqualTo("getMyProfile");
        assertThat(endpoint.path("patch").path("operationId").asText()).isEqualTo("patchMyProfile");
        List<String> headers = new ArrayList<>();
        endpoint.path("patch").path("parameters").forEach(parameter -> {
            if (parameter.path("in").asText().equals("header") && parameter.path("required").asBoolean()) {
                headers.add(parameter.path("name").asText());
            }
        });
        assertThat(headers).contains("Idempotency-Key", "If-Match", "X-CSRF-Token");
        Set<String> properties = new HashSet<>();
        document.path("components").path("schemas").path("StudentProfilePatch").path("properties").fieldNames().forEachRemaining(properties::add);
        assertThat(properties).containsExactlyInAnyOrder("sameGenderDefault", "defaultDropoff");
    }

    @Test void togglesBothWaysAndKeepsOmittedFields() throws Exception {
        var before = profiles.findById(student.getId()).orElseThrow();
        var first = patch(student, "{\"sameGenderDefault\":false}", key(), etag()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sameGenderDefault").value(false)).andReturn();
        var after = profiles.findById(student.getId()).orElseThrow();
        assertThat(after.getRealNameEncrypted()).isEqualTo(before.getRealNameEncrypted());
        assertThat(after.getDropoffLatEncrypted()).isEqualTo(before.getDropoffLatEncrypted());
        patch(student, "{\"sameGenderDefault\":true}", key(), first.getResponse().getHeader("ETag"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sameGenderDefault").value(true));
        verifyNoInteractions(locations);
    }

    @Test void realSessionCookieCanReadAndUpdateWithSessionCsrf() throws Exception {
        var initial = getProfile(student).andExpect(status().isOk()).andReturn();
        var cookie = initial.getResponse().getCookie("owlly_session");
        assertThat(cookie).isNotNull();
        var session = mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk()).andReturn();
        String csrfToken = mapper.readTree(session.getResponse().getContentAsString()).at("/data/csrfToken").asText();
        mvc.perform(MockMvcRequestBuilders.patch(PATH).cookie(cookie).header("X-CSRF-Token", csrfToken)
                        .header("Idempotency-Key", key()).header("If-Match", initial.getResponse().getHeader("ETag"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sameGenderDefault\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sameGenderDefault").value(false));
        mvc.perform(get(PATH).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.data.sameGenderDefault").value(false));
    }

    @Test void changesCoordinatesAndDongAtomicallyWithEncryption() throws Exception {
        String initial = etag();
        var result = patch(student, pointBody(), key(), initial).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultDongName").value("새하차동"))
                .andExpect(jsonPath("$.data.defaultDongId").value(newDong.getId().toString()))
                .andExpect(jsonPath("$.data.defaultDropoff.longitude").value(127.2))
                .andExpect(jsonPath("$.data.sameGenderDefault").value(true)).andReturn();
        assertThat(result.getResponse().getHeader("ETag")).isNotEqualTo(initial);
        var row = profiles.findById(student.getId()).orElseThrow();
        assertThat(row.getDropoffLatEncrypted()).isNotEqualTo("37.4");
        assertThat(cryptor.decrypt(row.getDropoffLatEncrypted())).isEqualTo("37.4");
        assertThat(cryptor.decrypt(row.getDropoffLngEncrypted())).isEqualTo("127.2");
        assertThat(row.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(cryptor.decrypt(row.getRealNameEncrypted())).isEqualTo("한서윤");
    }

    @Test void rejectsImmutableUnknownEmptyNullAndWrongTypedFields() throws Exception {
        String etag = etag();
        for (String body : List.of("{}", "{\"realName\":\"수정이름\"}", "{\"genderCode\":\"MALE\"}",
                "{\"universityId\":\"1\"}", "{\"email\":\"other@example.com\"}", "{\"userId\":\"1\"}",
                "{\"sameGenderDefault\":false,\"changeRequested\":true}",
                "{\"sameGenderDefault\":null}", "{\"defaultDropoff\":null}", "{\"sameGenderDefault\":false,\"defaultDropoff\":null}",
                "{\"sameGenderDefault\":\"false\"}", "{\"sameGenderDefault\":0}", "{\"sameGenderDefault\":[]}",
                "{\"defaultDropoff\":{}}", "{\"defaultDropoff\":{\"latitude\":37.4}}",
                "{\"defaultDropoff\":{\"latitude\":null,\"longitude\":127.2}}",
                "{\"defaultDropoff\":{\"latitude\":\"37.4\",\"longitude\":127.2}}",
                "{\"defaultDropoff\":{\"latitude\":91,\"longitude\":127.2}}",
                "{\"defaultDropoff\":{\"latitude\":37.4,\"longitude\":181}}",
                "{\"defaultDropoff\":{\"latitude\":1e400,\"longitude\":127.2}}")) {
            patch(student, body, key(), etag).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(profiles.findById(student.getId()).orElseThrow().isSameGenderDefault()).isTrue();
        verifyNoInteractions(locations);
    }

    @Test void validatesHeadersAndRejectsStaleUpdatesWithoutReceipts() throws Exception {
        String initial = etag();
        String body = "{\"sameGenderDefault\":false}";
        patch(student, body, null, initial).andExpect(status().isUnprocessableEntity());
        patch(student, body, "not-uuid", initial).andExpect(status().isUnprocessableEntity());
        patch(student, body, key(), null).andExpect(status().isPreconditionRequired());
        patch(student, body, key(), initial).andExpect(status().isOk());
        String staleKey = key();
        patch(student, "{\"sameGenderDefault\":true}", staleKey, initial).andExpect(status().isPreconditionFailed());
        assertThat(receipts.existsById(staleKey)).isFalse();
        assertThat(profiles.findById(student.getId()).orElseThrow().isSameGenderDefault()).isFalse();
    }

    @Test void retriesDoNotUndoLaterUpdatesAndConflictOnChangedPayloadOrUser() throws Exception {
        String initial = etag();
        String key = key();
        var first = patch(student, "{\"sameGenderDefault\":false}", key, initial).andExpect(status().isOk()).andReturn();
        Instant expiry = receipts.findById(key).orElseThrow().getExpiresAt();
        patch(student, "{\"sameGenderDefault\":true}", key(), first.getResponse().getHeader("ETag")).andExpect(status().isOk());
        patch(student, "{\"sameGenderDefault\":false}", key.toUpperCase(Locale.ROOT), initial).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sameGenderDefault").value(true));
        assertThat(receipts.findById(key).orElseThrow().getExpiresAt()).isEqualTo(expiry);
        patch(student, "{\"sameGenderDefault\":true}", key, initial).andExpect(status().isConflict());
        patch(other, "{\"sameGenderDefault\":false}", key, getProfile(other).andReturn().getResponse().getHeader("ETag"))
                .andExpect(status().isConflict());
        assertThat(profiles.findById(other.getId()).orElseThrow().isSameGenderDefault()).isTrue();
    }

    @Test void successfulCoordinateRetryDoesNotCallKakaoAgain() throws Exception {
        String initial = etag();
        String key = key();
        patch(student, pointBody(), key, initial).andExpect(status().isOk());
        when(locations.resolveDong(anyDouble(), anyDouble())).thenThrow(new OwllyException(ErrorCode.UPSTREAM_UNAVAILABLE));
        patch(student, pointBody(), key, initial).andExpect(status().isOk());
        verify(locations, times(1)).resolveDong(37.4, 127.2);
        assertThat(receipts.findById(key).orElseThrow().getRequestHash()).hasSize(64).doesNotContain("37.4", "127.2");
    }

    @Test void locationFailureRollsBackEveryChangeAndKeepsKeyAvailable() throws Exception {
        String initial = etag();
        String key = key();
        String body = "{\"sameGenderDefault\":false,\"defaultDropoff\":{\"latitude\":37.4,\"longitude\":127.2}}";
        for (ErrorCode error : List.of(ErrorCode.LOCATION_UNRESOLVABLE, ErrorCode.UPSTREAM_UNAVAILABLE)) {
            doThrow(new OwllyException(error)).when(locations).resolveDong(anyDouble(), anyDouble());
            patch(student, body, key, initial).andExpect(status().is(error.getStatus().value()))
                    .andExpect(jsonPath("$.error.code").value(error.name()));
            assertThat(receipts.existsById(key)).isFalse();
            assertThat(etag()).isEqualTo(initial);
        }
        doReturn(newDong).when(locations).resolveDong(anyDouble(), anyDouble());
        patch(student, body, key, initial).andExpect(status().isOk());
    }

    @Test void receiptWriteFailureRollsBackFlushedProfileChanges() throws Exception {
        String initial = etag();
        String key = key();
        doThrow(new IllegalStateException("simulated receipt write failure")).when(receipts).saveAndFlush(any(StudentProfileUpdateReceipt.class));
        patch(student, pointBody(), key, initial).andExpect(status().isInternalServerError());
        assertThat(etag()).isEqualTo(initial);
        assertThat(receipts.existsById(key)).isFalse();
        reset(receipts);
        patch(student, pointBody(), key, initial).andExpect(status().isOk());
    }

    @Test void encryptionFailureRollsBackToggleAndCoordinates() throws Exception {
        String initial = etag();
        String key = key();
        doThrow(new IllegalStateException("simulated encryption failure")).when(cryptor).encrypt("127.2");
        patch(student, "{\"sameGenderDefault\":false,\"defaultDropoff\":{\"latitude\":37.4,\"longitude\":127.2}}", key, initial)
                .andExpect(status().isInternalServerError());
        assertThat(etag()).isEqualTo(initial);
        assertThat(receipts.existsById(key)).isFalse();
    }

    @Test void concurrentDifferentCommandsWithSameEtagAllowOnlyOneUpdate() throws Exception {
        String initial = etag();
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> work = List.of(
                    () -> patch(student, "{\"sameGenderDefault\":false}", key(), initial).andReturn().getResponse().getStatus(),
                    () -> patch(student, "{\"sameGenderDefault\":false}", key(), initial).andReturn().getResponse().getStatus());
            var results = pool.invokeAll(work);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 412);
        }
    }

    @Test void concurrentSameCommandIsAppliedOnce() throws Exception {
        String initial = etag();
        String key = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> work = List.of(
                    () -> patch(student, pointBody(), key, initial).andReturn().getResponse().getStatus(),
                    () -> patch(student, pointBody(), key, initial).andReturn().getResponse().getStatus());
            var results = pool.invokeAll(work);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactly(200, 200);
        }
        verify(locations, times(1)).resolveDong(37.4, 127.2);
    }

    @Test void cleanupSkipsExpiredReceiptInUseAndRemovesUnusedExpiredReceipt() throws Exception {
        String inUse = key();
        String unused = key();
        patch(student, "{\"sameGenderDefault\":false}", inUse, etag()).andExpect(status().isOk());
        patch(student, "{\"sameGenderDefault\":true}", unused, etag()).andExpect(status().isOk());
        jdbc.update("update student_profile_update_receipts set expires_at=? where request_key in (?, ?)",
                Timestamp.from(Instant.now().minusSeconds(60)), inUse, unused);
        doAnswer(invocation -> {
            var remaining = cleanupSnapshot(() -> jdbc.queryForList(
                    "select request_key from student_profile_update_receipts where request_key in (?, ?)", String.class, inUse, unused));
            assertThat(remaining).containsExactly(inUse);
            return invocation.callRealMethod();
        }).when(cryptor).encrypt("37.4");
        patch(student, pointBody(), inUse, etag()).andExpect(status().isOk());
        assertThat(receipts.findById(inUse).orElseThrow().getExpiresAt()).isAfter(Instant.now());
    }

    @Test void cleanupKeepsUnexpiredReceipt() throws Exception {
        String key = key();
        patch(student, "{\"sameGenderDefault\":false}", key, etag()).andExpect(status().isOk());
        assertThat(cleanupSnapshot(() -> receipts.existsById(key))).isTrue();
    }

    @Test void checksAuthenticationRoleStatusAndCsrf() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.patch(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"sameGenderDefault\":false}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.patch(PATH).with(authentication(auth(student))).header("Idempotency-Key", key()).header("If-Match", etag())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sameGenderDefault\":false}")).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.patch(PATH).with(authentication(auth(student))).with(csrf().asHeader().useInvalidToken())
                .header("Idempotency-Key", key()).header("If-Match", etag())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sameGenderDefault\":false}")).andExpect(status().isForbidden());
        jdbc.update("update users set role='STAFF' where id=?", student.getId());
        getProfile(student).andExpect(status().isForbidden());
        patch(student, "{\"sameGenderDefault\":false}", key(), "\"irrelevant\"").andExpect(status().isForbidden());
        jdbc.update("update users set role='STUDENT', status='SUSPENDED' where id=?", student.getId());
        getProfile(student).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        jdbc.update("update users set status='WITHDRAWN' where id=?", student.getId());
        getProfile(student).andExpect(status().isUnauthorized());
    }

    @Test void missingProfileReturnsNotFound() throws Exception {
        profiles.deleteById(student.getId());
        getProfile(student).andExpect(status().isNotFound());
        patch(student, "{\"sameGenderDefault\":false}", key(), "\"irrelevant\"").andExpect(status().isNotFound());
    }

    @Test void changesDoNotRewriteExistingJourneyOrRideSnapshot() throws Exception {
        Long pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(student.getUniversityId()).name("프로필테스트거점")
                .areaId(city.getId()).latitude(new java.math.BigDecimal("37.3")).longitude(new java.math.BigDecimal("127.1"))
                .meetingNote("테스트").active(true).build()).getId();
        pickupId = pickup;
        var journey = journeys.saveAndFlush(Journey.builder().clientJourneyKey(UUID.randomUUID()).userId(student.getId())
                .universityId(student.getUniversityId()).pickupPointId(pickup).destinationDongId(oldDong.getId())
                .intendedDepartureAt(Instant.now().plusSeconds(3600)).outcome(JourneyOutcome.UNRESOLVED).build());
        journeyId = journey.getId();
        var ride = rides.saveAndFlush(RideRequest.builder().clientRequestKey(UUID.randomUUID()).journeyId(journeyId)
                .universityId(student.getUniversityId()).pickupPointId(pickup).districtId(city.getId()).destinationDongId(oldDong.getId())
                .dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1"))
                .departureAt(Instant.now().plusSeconds(3600)).sameGenderOnly(true).genderSnapshot(Gender.FEMALE).status(RideRequestStatus.WAITING).build());
        rideId = ride.getId();
        patch(student, "{\"sameGenderDefault\":false,\"defaultDropoff\":{\"latitude\":37.4,\"longitude\":127.2}}", key(), etag())
                .andExpect(status().isOk());
        assertThat(journeys.findById(journeyId).orElseThrow().getDestinationDongId()).isEqualTo(oldDong.getId());
        var existing = rides.findById(rideId).orElseThrow();
        assertThat(existing.getDestinationDongId()).isEqualTo(oldDong.getId());
        assertThat(existing.isSameGenderOnly()).isTrue();
        assertThat(existing.getGenderSnapshot()).isEqualTo(Gender.FEMALE);
        assertThat(existing.getDropoffLatEncrypted()).isEqualTo(ride.getDropoffLatEncrypted());
    }

    private User account(Long universityId) {
        return users.saveAndFlush(User.builder().universityId(universityId).email("profile-test-" + UUID.randomUUID() + "@hufs.ac.kr")
                .role(Role.STUDENT).status(UserStatus.ACTIVE).build());
    }
    private AdministrativeArea area(String name, AreaLevel level, Long parent) {
        return areas.saveAndFlush(AdministrativeArea.builder().officialCode("99" + String.format("%08d", new java.security.SecureRandom().nextInt(100_000_000)))
                .name(name).level(level).parentId(parent).build());
    }
    private UsernamePasswordAuthenticationToken auth(User user) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(user.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }
    private ResultActions getProfile(User user) throws Exception { return mvc.perform(get(PATH).with(authentication(auth(user)))); }
    private String etag() throws Exception { return getProfile(student).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag"); }
    private String key() { String key = UUID.randomUUID().toString(); keys.add(key); return key; }
    private String pointBody() { return "{\"defaultDropoff\":{\"latitude\":37.4,\"longitude\":127.2}}"; }
    private ResultActions patch(User user, String body, String key, String etag) throws Exception {
        var request = MockMvcRequestBuilders.patch(PATH).with(authentication(auth(user))).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request.header("Idempotency-Key", key);
        if (etag != null) request.header("If-Match", etag);
        return mvc.perform(request);
    }
    private <T> T cleanupSnapshot(Supplier<T> snapshot) throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            return pool.submit(() -> new TransactionTemplate(transactions).execute(transaction -> {
                transaction.setRollbackOnly();
                jdbc.execute("set local statement_timeout = '3s'");
                cleanup.deleteExpired();
                return snapshot.get();
            })).get(8, TimeUnit.SECONDS);
        }
    }
}
