package com.yongyong.owlly.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.auth.dto.StudentRegistrationRequest;
import com.yongyong.owlly.domain.auth.repository.StudentRegistrationReceiptRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.verification.entity.EmailVerification;
import com.yongyong.owlly.domain.verification.repository.EmailVerificationRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 실제 PostgreSQL의 advisory lock·JPA 트랜잭션·JDBC 세션을 사용하고 공통 행정동 판별 연결만 대체한다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false", "owlly.verification.secret=01234567890123456789012345678901"})
@AutoConfigureMockMvc
class StudentRegistrationIntegrationTest {
    private static final String SECRET = "01234567890123456789012345678901";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired UniversityRepository universities;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired EmailVerificationRepository verifications;
    @Autowired StudentRegistrationReceiptRepository receipts;
    @MockitoSpyBean TextCryptor cryptor;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DropoffLocationService locations;
    String email;
    String token;
    Long universityId;
    Long verificationId;
    AdministrativeArea dong;

    @BeforeEach void setup() throws Exception {
        universityId = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        email = "registration-test-" + UUID.randomUUID() + "@hufs.ac.kr";
        token = UUID.randomUUID().toString();
        dong = areas.saveAndFlush(AdministrativeArea.builder().officialCode("99" + String.format("%08d", new java.security.SecureRandom().nextInt(100_000_000)))
                .name("가입테스트동").level(AreaLevel.DONG).build());
        when(locations.resolveDong(anyDouble(), anyDouble())).thenReturn(dong);
        var verification = EmailVerification.builder().universityId(universityId).email(email).codeHash("test-only")
                .sentAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        verification.verify(UUID.randomUUID().toString(), hash("grant:" + token), Instant.now(), Instant.now().plusSeconds(600));
        verificationId = verifications.saveAndFlush(verification).getId();
    }

    @AfterEach void cleanup() {
        // 이 테스트가 생성한 이메일·행정동에 해당하는 행만 제거한다. 기존 개발 데이터를 지우지 않는다.
        List<Long> ids = jdbc.queryForList("select id from users where email = ?", Long.class, email);
        for (Long id : ids) {
            jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name = ?)", id.toString());
            jdbc.update("delete from spring_session where principal_name = ?", id.toString());
            jdbc.update("delete from student_registration_receipts where user_id = ?", id);
            jdbc.update("delete from student_profiles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
        if (email != null) jdbc.update("delete from email_verifications where email = ?", email);
        if (dong != null) areas.deleteById(dong.getId());
    }

    @Test void registersEncryptedProfileAndCreatesUsableNamedSession() throws Exception {
        MvcResult result = register(request("한서윤", "FEMALE", null), UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.user.universityName").value("한국외국어대학교"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).at("/data/user/id").asLong();
        var profile = profiles.findById(id).orElseThrow();
        assertThat(profile.getDefaultDongId()).isEqualTo(dong.getId());
        assertThat(profile.isSameGenderDefault()).isTrue();
        assertThat(profile.getRealNameEncrypted()).doesNotContain("한서윤");
        assertThat(cryptor.decrypt(profile.getRealNameEncrypted())).isEqualTo("한서윤");
        assertThat(cryptor.decrypt(profile.getDropoffLatEncrypted())).isEqualTo("37.3");
        assertThat(cryptor.decrypt(profile.getDropoffLngEncrypted())).isEqualTo("127.1");
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNotNull();
        Cookie cookie = result.getResponse().getCookie("owlly_session");
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.universityName").value("한국외국어대학교"));
        mvc.perform(get("/api/v1/pickup-points").cookie(cookie)).andExpect(status().isOk());
        String csrf = mapper.readTree(result.getResponse().getContentAsString()).at("/data/csrfToken").asText();
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("X-CSRF-Token", csrf)
                        .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void profileOptionsArePublicAndMatchRegistrationCodes() throws Exception {
        mvc.perform(get("/api/v1/profile-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.genderOptions[0].code").value("FEMALE"))
                .andExpect(jsonPath("$.data.genderOptions[1].code").value("MALE"))
                .andExpect(jsonPath("$.data.sameGenderDefault").value(true));
    }

    @Test void retriesUseOneAccountAndChangedPayloadConflicts() throws Exception {
        String key = UUID.randomUUID().toString();
        var body = request("한서윤", "FEMALE", true);
        var first = register(body, key).andExpect(status().isCreated()).andReturn();
        var second = register(body, key).andExpect(status().isCreated()).andReturn();
        assertThat(mapper.readTree(first.getResponse().getContentAsString()).at("/data/user/id"))
                .isEqualTo(mapper.readTree(second.getResponse().getContentAsString()).at("/data/user/id"));
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
        register(request("다른이름", "FEMALE", true), key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        register(body, UUID.randomUUID().toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERIFICATION_USED"));
    }

    @Test void unresolvableLocationRollsBackGrantAndAllowsRetry() throws Exception {
        when(locations.resolveDong(anyDouble(), anyDouble())).thenThrow(new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE));
        String key = UUID.randomUUID().toString();
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LOCATION_UNRESOLVABLE"));
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull();
        assertThat(users.existsByEmail(email)).isFalse();
        assertThat(receipts.existsById(key)).isFalse();
        doReturn(dong).when(locations).resolveDong(anyDouble(), anyDouble());
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isCreated());
    }

    @Test void upstreamErrorLeavesGrantUnconsumed() throws Exception {
        when(locations.resolveDong(anyDouble(), anyDouble())).thenThrow(new OwllyException(ErrorCode.UPSTREAM_UNAVAILABLE));
        register(request("한서윤", "MALE", true), UUID.randomUUID().toString()).andExpect(status().isServiceUnavailable());
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull();
    }

    @Test void pendingCommonServiceDoesNotConsumeGrantOrCreateAccount() throws Exception {
        when(locations.resolveDong(anyDouble(), anyDouble())).thenThrow(new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED));
        String key = UUID.randomUUID().toString();
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("POLICY_NOT_CONFIGURED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().doesNotExist("owlly_session"));
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull();
        assertThat(users.existsByEmail(email)).isFalse();
        assertThat(receipts.existsById(key)).isFalse();
    }

    @Test void failureAfterUserInsertRollsBackUserProfileReceiptAndGrant() throws Exception {
        String key = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("simulated encryption failure")).when(cryptor).encrypt("저장실패");
        register(request("저장실패", "FEMALE", true), key).andExpect(status().isInternalServerError());
        assertThat(users.existsByEmail(email)).isFalse();
        assertThat(receipts.existsById(key)).isFalse();
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull();
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isCreated());
    }

    @Test void omittedSameGenderDefaultsTrueAndClientCannotSetServerOwnedFields() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(request("한서윤", "MALE", true));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("profile")).remove("sameGenderDefault");
        var injected = body.deepCopy().put("role", "STAFF");
        mvc.perform(post("/api/v1/auth/student-registrations").header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(injected.toString()))
                .andExpect(status().isUnprocessableEntity());
        var result = mvc.perform(post("/api/v1/auth/student-registrations").header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).at("/data/user/id").asLong();
        assertThat(profiles.findById(id).orElseThrow().isSameGenderDefault()).isTrue();
    }

    @Test void invalidProfileDoesNotConsumeGrant() throws Exception {
        register(request("한", "FEMALE", true), UUID.randomUUID().toString()).andExpect(status().isUnprocessableEntity());
        register(request("이름이너무길어서스무글자를확실히초과하는사용자입니다", "FEMALE", true), UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity());
        register(request("한서윤", "OTHER", true), UUID.randomUUID().toString()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_GENDER_CODE"));
        var point = new StudentRegistrationRequest.Point(91.0, 127.1);
        register(new StudentRegistrationRequest(token, new StudentRegistrationRequest.Profile("한서윤", "FEMALE", true, point)), UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity());
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull();
        verifyNoInteractions(locations);
    }

    @Test void sameGenderCanBeDisabledAndRetryCannotChangeTheChoice() throws Exception {
        String key = UUID.randomUUID().toString();
        var body = request("한서윤", "FEMALE", false);
        var result = register(body, key).andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).at("/data/user/id").asLong();
        assertThat(profiles.findById(id).orElseThrow().isSameGenderDefault()).isFalse();
        register(body, key).andExpect(status().isCreated());
        assertThat(profiles.findById(id).orElseThrow().isSameGenderDefault()).isFalse();
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test void expiredGrantAndMissingUuidAreRejected() throws Exception {
        jdbc.update("update email_verifications set grant_expires_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), verificationId);
        register(request("한서윤", "FEMALE", true), UUID.randomUUID().toString()).andExpect(status().isGone());
        register(request("한서윤", "FEMALE", true), "bad").andExpect(status().isUnprocessableEntity());
    }

    @Test void duplicateEmailRejectsWithoutConsumingNewProof() throws Exception {
        users.saveAndFlush(com.yongyong.owlly.domain.user.entity.User.builder().email(email).universityId(universityId)
                .role(Role.STAFF).status(UserStatus.ACTIVE).build());
        register(request("한서윤", "FEMALE", true), UUID.randomUUID().toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_ALREADY_EXISTS"));
        assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull();
    }

    @Test void expiredOrSuspendedReplayCannotIssueSession() throws Exception {
        String key = UUID.randomUUID().toString();
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isCreated());
        jdbc.update("update users set status = 'SUSPENDED' where email = ?", email);
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        jdbc.update("update users set status = 'ACTIVE' where email = ?", email);
        jdbc.update("update student_registration_receipts set expires_at = ? where request_key = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), key);
        register(request("한서윤", "FEMALE", true), key).andExpect(status().isGone());
    }

    @Test void concurrentSameKeyCreatesExactlyOneProfile() throws Exception {
        String key = UUID.randomUUID().toString();
        var body = request("한서윤", "FEMALE", true);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(
                    (java.util.concurrent.Callable<Integer>) () -> register(body, key).andReturn().getResponse().getStatus(),
                    () -> register(body, key).andReturn().getResponse().getStatus()));
            assertThat(results.get(0).get()).isEqualTo(201);
            assertThat(results.get(1).get()).isEqualTo(201);
        }
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where user_id in (select id from users where email = ?)", Integer.class, email)).isEqualTo(1);
        verify(locations, times(1)).resolveDong(anyDouble(), anyDouble());
    }

    @Test void concurrentDifferentKeysCannotConsumeProofTwice() throws Exception {
        var body = request("한서윤", "FEMALE", true);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(
                    (java.util.concurrent.Callable<Integer>) () -> register(body, UUID.randomUUID().toString()).andReturn().getResponse().getStatus(),
                    () -> register(body, UUID.randomUUID().toString()).andReturn().getResponse().getStatus()));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
    }

    private StudentRegistrationRequest request(String name, String gender, Boolean sameGender) {
        return new StudentRegistrationRequest(token, new StudentRegistrationRequest.Profile(name, gender, sameGender,
                new StudentRegistrationRequest.Point(37.3, 127.1)));
    }

    private org.springframework.test.web.servlet.ResultActions register(StudentRegistrationRequest body, String key) throws Exception {
        return mvc.perform(post("/api/v1/auth/student-registrations").header("Origin", "http://localhost:3000")
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private static String hash(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
