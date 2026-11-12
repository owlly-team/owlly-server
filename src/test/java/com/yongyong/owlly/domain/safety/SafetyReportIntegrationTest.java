package com.yongyong.owlly.domain.safety;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.*;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.chat.dto.MessageCreate;
import com.yongyong.owlly.domain.chat.repository.*;
import com.yongyong.owlly.domain.chat.service.ChatRoomService;
import com.yongyong.owlly.domain.journey.entity.*;
import com.yongyong.owlly.domain.journey.repository.*;
import com.yongyong.owlly.domain.party.dto.MatchingCandidate;
import com.yongyong.owlly.domain.party.entity.*;
import com.yongyong.owlly.domain.party.repository.*;
import com.yongyong.owlly.domain.party.service.PartyGrouper;
import com.yongyong.owlly.domain.region.entity.*;
import com.yongyong.owlly.domain.region.repository.*;
import com.yongyong.owlly.domain.safety.dto.*;
import com.yongyong.owlly.domain.safety.repository.*;
import com.yongyong.owlly.domain.settlement.entity.*;
import com.yongyong.owlly.domain.settlement.repository.*;
import com.yongyong.owlly.domain.safety.service.SafetyReportService;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 PostgreSQL·암호화·매칭 차단 조회·세션/CSRF로 신고 정책과 트랜잭션을 검증한다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false", "owlly.verification.secret=01234567890123456789012345678901"})
@AutoConfigureMockMvc
class SafetyReportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SafetyReportService service;
    @Autowired SafetyReportRepository reports;
    @MockitoSpyBean SafetyReportReceiptRepository receipts;
    @Autowired ChatRoomService chat;
    @Autowired ChatMessageRepository messages;
    @Autowired RidePartyRepository parties;
    @Autowired PartyMemberRepository members;
    @Autowired JourneyRepository journeys;
    @Autowired RideRequestRepository rides;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired UniversityRepository universities;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired PickupPointRepository pickups;
    @Autowired RoomAccountRepository accounts;
    @Autowired SettlementRepository settlements;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TextCryptor cryptor;
    User student, representative, outsider;
    AdministrativeArea city, dong;
    PickupPoint pickup;
    Instant departure;
    Fixture primary;
    final List<Fixture> fixtures = new ArrayList<>();
    final List<Long> journeyIds = new ArrayList<>(), rideIds = new ArrayList<>();

    @BeforeEach void setup() {
        departure = Instant.now().truncatedTo(ChronoUnit.MILLIS).plusSeconds(7200);
        Long university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        city = area("신고테스트시", AreaLevel.CITY, null);
        dong = area("신고테스트동", AreaLevel.DONG, city.getId());
        pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(university).areaId(city.getId()).name("신고테스트정문")
                .meetingNote("정문 앞").latitude(new BigDecimal("37.3")).longitude(new BigDecimal("127.1")).active(true).build());
        student = user(university); representative = user(university); outsider = user(university);
        primary = fixture();
    }

    @AfterEach void cleanup() {
        reset(receipts);
        jdbc.update("delete from user_blocks where user_low_id in (?,?,?) or user_high_id in (?,?,?)",
                student.getId(), representative.getId(), outsider.getId(), student.getId(), representative.getId(), outsider.getId());
        for (Fixture f : fixtures) {
            jdbc.update("delete from safety_report_receipts where report_id in (select id from safety_reports where room_id=?)", f.room());
            jdbc.update("delete from safety_reports where room_id=?", f.room());
            jdbc.update("delete from chat_send_receipts where message_id in (select id from chat_messages where room_id=?)", f.room());
            jdbc.update("delete from chat_messages where room_id=?", f.room());
            jdbc.update("delete from room_accounts where room_id=?", f.room());
            jdbc.update("delete from chat_rooms where id=?", f.room());
            jdbc.update("delete from settlements where party_id=?", f.party());
            jdbc.update("update ride_parties set representative_member_id=null where id=?", f.party());
            jdbc.update("delete from party_members where party_id=?", f.party());
            parties.deleteById(f.party());
        }
        rideIds.forEach(rides::deleteById); journeyIds.forEach(journeys::deleteById);
        for (User u : List.of(student, representative, outsider)) {
            jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name=?)", u.getId().toString());
            jdbc.update("delete from spring_session where principal_name=?", u.getId().toString());
            profiles.deleteById(u.getId()); users.deleteById(u.getId());
        }
        pickups.deleteById(pickup.getId()); areas.deleteById(dong.getId()); areas.deleteById(city.getId());
        fixtures.clear(); journeyIds.clear(); rideIds.clear();
    }

    @Test void capturesEntireRoomBeforeReportMasksEncryptsAndReturnsOnlyReceiptFields() throws Exception {
        Long targetMessage = send(representative, primary.room(), "0042 확인 010/1234/5678");
        send(student, primary.room(), "신고 전 대화");
        var input = input(primary.rep(), targetMessage, "연락처 010_9876_5432 https://example.com");
        Instant before = Instant.now().minusNanos(1000);
        var result = postReport(student, primary.room(), input, key()).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.rematchingBlocked").value(true))
                .andExpect(jsonPath("$.data.details").doesNotExist())
                .andExpect(jsonPath("$.data.evidenceEncrypted").doesNotExist()).andReturn();
        Long id = mapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        var report = reports.findById(id).orElseThrow();
        assertThat(report.getReporterMemberId()).isEqualTo(primary.me());
        assertThat(report.getMessageId()).isEqualTo(targetMessage);
        String encrypted = report.getEvidenceEncrypted();
        assertThat(encrypted).doesNotContain("신고 전 대화", "010", "example.com");
        var evidence = mapper.readTree(cryptor.decrypt(encrypted));
        assertThat(evidence.path("messages").size()).isEqualTo(4); // SYSTEM 2건 + 양쪽 대화
        assertThat(evidence.path("details").asText()).contains("가려진 번호").doesNotContain("9876", "example.com");
        assertThat(evidence.toString()).contains("신고 전 대화", "0042", "가려진 번호").doesNotContain("010/1234", "010_9876");
        Instant captured = Instant.parse(evidence.path("capturedAt").asText());
        assertThat(captured).isBetween(before, Instant.now());
        assertThat(report.getEvidenceDeleteAfter()).isEqualTo(captured.plus(7, ChronoUnit.DAYS));
        send(student, primary.room(), "신고 후 대화");
        assertThat(cryptor.decrypt(reports.findById(id).orElseThrow().getEvidenceEncrypted())).doesNotContain("신고 후 대화");
        assertThat(blockCount()).isEqualTo(1);
    }

    @Test void acceptsMemberOnlyReportAndAllFiveReasonsWithoutRequiringOtherDescription() {
        var reasons = List.of("ABUSIVE_LANGUAGE", "PRIVACY_REQUEST", "NO_SHOW", "IDENTITY_MISMATCH", "OTHER");
        for (int i = 0; i < reasons.size(); i++) {
            Fixture f = i == 0 ? primary : fixture();
            var report = service.create(student.getId(), f.room(), new ReportCreate(f.rep().toString(), reasons.get(i), null, null), key());
            assertThat(reports.findById(report.id()).orElseThrow().getReasonCode().name()).isEqualTo(reasons.get(i));
        }
        assertThat(blockCount()).isEqualTo(1);
    }

    @Test void validatesJsonTypesIdsEnumLengthHeadersAndUnknownFields() throws Exception {
        String target = primary.rep().toString();
        for (String body : List.of("{}", "{\"targetMemberId\":123,\"reasonCode\":\"OTHER\"}",
                "{\"targetMemberId\":\"" + target + "\",\"reasonCode\":1}",
                "{\"targetMemberId\":\"" + target + "\",\"reasonCode\":\"OTHER\",\"details\":true}",
                "{\"targetMemberId\":\"" + target + "\",\"reasonCode\":\"OTHER\",\"messageId\":1}",
                "{\"targetMemberId\":\"" + target + "\",\"reasonCode\":\"UNKNOWN\"}",
                "{\"targetMemberId\":\"" + target + "\",\"reasonCode\":\"OTHER\",\"reporterMemberId\":\"1\"}")) {
            mvc.perform(post(path(primary.room())).with(authentication(auth(student))).with(csrf().asHeader())
                    .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());
        }
        for (String id : List.of("0", "-1", "01", "9223372036854775808", "1e2", "bad"))
            postReport(student, primary.room(), new ReportCreate(id, "OTHER", null, null), key()).andExpect(status().isUnprocessableEntity());
        postReport(student, primary.room(), input(primary.rep(), null, "가".repeat(201)), key()).andExpect(status().isUnprocessableEntity());
        postReport(student, primary.room(), input(primary.rep(), null, "가".repeat(200)), "bad").andExpect(status().isUnprocessableEntity());
        mvc.perform(post(path(primary.room())).with(authentication(auth(student))).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input(primary.rep(), null, null))))
                .andExpect(status().isUnprocessableEntity());
        postReport(student, primary.room(), input(primary.rep(), null, "가".repeat(200)), key()).andExpect(status().isCreated());
    }

    @Test void enforcesAnonymousRoleStatusMembershipRepresentativeAccountAndCsrf() throws Exception {
        String body = mapper.writeValueAsString(input(primary.rep(), null, null));
        mvc.perform(post(path(primary.room())).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(primary.room())).with(authentication(auth(student))).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        postReport(outsider, primary.room(), input(primary.rep(), null, null), key()).andExpect(status().isNotFound());
        for (String decision : List.of("PENDING", "LEFT", "DECLINED")) {
            jdbc.update("update party_members set decision=? where id=?", decision, primary.me());
            postReport(student, primary.room(), input(primary.rep(), null, null), key()).andExpect(status().isForbidden());
        }
        jdbc.update("update party_members set decision='ACCEPTED' where id=?", primary.me());
        jdbc.update("update users set status='SUSPENDED' where id=?", student.getId());
        postReport(student, primary.room(), input(primary.rep(), null, null), key()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        jdbc.update("update users set status='ACTIVE', role='STAFF' where id=?", student.getId());
        postReport(student, primary.room(), input(primary.rep(), null, null), key()).andExpect(status().isForbidden());
        jdbc.update("update users set role='STUDENT' where id=?", student.getId());
        jdbc.update("delete from room_accounts where room_id=?", primary.room());
        postReport(representative, primary.room(), input(primary.me(), null, null), key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_READY"));
        assertThat(reportCount(primary.room())).isZero();
    }

    @Test void rejectsSelfOtherPartySystemOwnAndMissingMessagesButAllowsFormerTargetMember() throws Exception {
        Fixture other = fixture();
        Long ownMessage = send(student, primary.room(), "내 메시지");
        Long foreignMessage = send(representative, other.room(), "다른 방 메시지");
        Long systemMessage = messages.findBySystemEventKey(primary.room() + ":opened").orElseThrow().getId();
        for (ReportCreate bad : List.of(input(primary.me(), null, null), input(other.rep(), null, null), input(Long.MAX_VALUE, null, null),
                input(primary.rep(), ownMessage, null), input(primary.rep(), foreignMessage, null),
                input(primary.rep(), systemMessage, null), input(primary.rep(), Long.MAX_VALUE, null)))
            postReport(student, primary.room(), bad, key()).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REPORT_TARGET"));
        jdbc.update("update party_members set decision='LEFT' where id=?", primary.rep());
        postReport(student, primary.room(), input(primary.rep(), null, null), key()).andExpect(status().isCreated());
    }

    @Test void replaysSameKeyWithoutExtendingExpiryAndRejectsChangedRawInputUserOrRoom() throws Exception {
        String key = key();
        var input = input(primary.rep(), null, "010-1234-5678");
        var first = service.create(student.getId(), primary.room(), input, key);
        String encrypted = reports.findById(first.id()).orElseThrow().getEvidenceEncrypted();
        postReport(student, primary.room(), input, key.toUpperCase(Locale.ROOT)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(first.id().toString()));
        assertThat(service.create(student.getId(), primary.room(), input, key)).isEqualTo(first);
        assertThat(reports.findById(first.id()).orElseThrow().getEvidenceEncrypted()).isEqualTo(encrypted);
        assertError(() -> service.create(student.getId(), primary.room(), input(primary.rep(), null, "010-9999-9999"), key), ErrorCode.IDEMPOTENCY_CONFLICT);
        assertError(() -> service.create(representative.getId(), primary.room(), input(primary.me(), null, null), key), ErrorCode.IDEMPOTENCY_CONFLICT);
        Fixture other = fixture();
        assertError(() -> service.create(student.getId(), other.room(), input(other.rep(), null, null), key), ErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(reportCount(primary.room())).isEqualTo(1);
    }

    @Test void differentKeyCannotReportSameTargetAgainButReverseAndOtherRoomAreSeparate() throws Exception {
        service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key());
        postReport(student, primary.room(), new ReportCreate(primary.rep().toString(), "NO_SHOW", null, "다른 사유"), key())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_EXISTS"));
        service.create(representative.getId(), primary.room(), input(primary.me(), null, null), key());
        Fixture other = fixture();
        service.create(student.getId(), other.room(), input(other.rep(), null, null), key());
        assertThat(reportCount(primary.room())).isEqualTo(2);
        assertThat(reportCount(other.room())).isEqualTo(1);
        assertThat(blockCount()).isEqualTo(1);
    }

    @Test void concurrentSameKeyCreatesOneReportAndSameReceipt() throws Exception {
        String key = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> action = () -> { start.await(); return service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key).id(); };
            var a = pool.submit(action); var b = pool.submit(action); start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        assertThat(reportCount(primary.room())).isEqualTo(1); assertThat(blockCount()).isEqualTo(1);
    }

    @Test void concurrentDifferentKeysForSameTargetCreateOneReportAndConflict() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<String> action = () -> {
                start.await();
                try { service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key()); return "CREATED"; }
                catch (OwllyException e) { return e.getErrorCode().name(); }
            };
            var a = pool.submit(action); var b = pool.submit(action); start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder("CREATED", "REPORT_ALREADY_EXISTS");
        }
        assertThat(reportCount(primary.room())).isEqualTo(1); assertThat(blockCount()).isEqualTo(1);
    }

    @Test void crossRoomConcurrentReportsShareOneSymmetricBlock() throws Exception {
        Fixture other = fixture();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key()); });
            var b = pool.submit(() -> { start.await(); return service.create(representative.getId(), other.room(), input(other.me(), null, null), key()); });
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).id()).isNotEqualTo(b.get(10, TimeUnit.SECONDS).id());
        }
        assertThat(blockCount()).isEqualTo(1);
    }

    @Test void receiptFailureRollsBackReportEvidenceAndBlockAndAllowsRetry() {
        String key = key();
        doThrow(new IllegalStateException("storage unavailable")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key)).isInstanceOf(IllegalStateException.class);
        assertThat(reportCount(primary.room())).isZero(); assertThat(blockCount()).isZero();
        reset(receipts);
        assertThat(service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key).rematchingBlocked()).isTrue();
    }

    @Test void deletesEachExpiredEvidenceAtBoundaryWithoutDeletingChatBlockOrDuplicateProtection() {
        Long targetMessage = send(representative, primary.room(), "증거 메시지");
        String firstKey = key();
        var input = input(primary.rep(), targetMessage, "선택 설명");
        var first = service.create(student.getId(), primary.room(), input, firstKey);
        var second = service.create(representative.getId(), primary.room(), input(primary.me(), null, "나중 신고"), key());
        Instant deadline = first.evidenceDeleteAfter();
        jdbc.update("update safety_reports set evidence_delete_after=? where id=?", Timestamp.from(deadline.plusSeconds(172800)), second.id());
        assertThat(purge(deadline.minusNanos(1000))).isZero();
        assertThat(purge(deadline)).isEqualTo(1);
        var expired = reports.findById(first.id()).orElseThrow();
        assertThat(expired.getEvidenceEncrypted()).isNull(); assertThat(expired.getMessageId()).isNull();
        assertThat(reports.findById(second.id()).orElseThrow().getEvidenceEncrypted()).isNotNull();
        assertThat(messages.findById(targetMessage)).isPresent(); assertThat(blockCount()).isEqualTo(1);
        assertThat(service.create(student.getId(), primary.room(), input, firstKey)).isEqualTo(first);
        assertThat(reports.findById(first.id()).orElseThrow().getEvidenceEncrypted()).isNull();
        assertError(() -> service.create(student.getId(), primary.room(), input, key()), ErrorCode.REPORT_ALREADY_EXISTS);
        assertThat(purge(deadline.plusSeconds(172800))).isEqualTo(1);
        assertThat(purge(deadline.plusSeconds(172800))).isZero();
    }

    @Test void persistedBlockExcludesBothUsersFromSubsequentMatchingEvenAfterEvidenceExpiry() {
        var report = service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key());
        var candidates = List.of(new MatchingCandidate(1L, student.getId(), Gender.FEMALE, true),
                new MatchingCandidate(2L, representative.getId(), Gender.FEMALE, true));
        assertThat(PartyGrouper.group(candidates, Set.of()).parties()).hasSize(1);
        purge(report.evidenceDeleteAfter());
        var pairs = parties.findBlockedPairs(List.of(student.getId(), representative.getId())).stream()
                .map(p -> PartyGrouper.UserPair.of(p.getLowUserId(), p.getHighUserId())).collect(Collectors.toSet());
        assertThat(PartyGrouper.group(candidates, pairs).parties()).isEmpty();
        assertThat(pairs).containsExactly(PartyGrouper.UserPair.of(student.getId(), representative.getId()));
    }

    @Test void rechecksMembershipAndLifetimeOnRetryAndRejectsMissingOrLogicallyClosedRooms() throws Exception {
        String key = key(); var input = input(primary.rep(), null, null);
        service.create(student.getId(), primary.room(), input, key);
        jdbc.update("update party_members set decision='LEFT' where id=?", primary.me());
        postReport(student, primary.room(), input, key).andExpect(status().isForbidden());
        jdbc.update("update party_members set decision='ACCEPTED' where id=?", primary.me());
        jdbc.update("update ride_parties set departure_at=? where id=?", Timestamp.from(Instant.now().minusSeconds(7200)), primary.party());
        settlements.saveAndFlush(Settlement.builder().partyId(primary.party()).estimatedTotalWon(10000).routeVersion(1)
                .status(SettlementStatus.COMPLETED).finalizedAt(Instant.now().minusSeconds(7200)).build());
        postReport(student, primary.room(), input, key).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_CLOSED"));
        postReport(student, primary.room(), input, key()).andExpect(status().isConflict());
        postReport(student, Long.MAX_VALUE, input, key()).andExpect(status().isNotFound());
        assertThat(reportCount(primary.room())).isEqualTo(1);
    }

    @Test void waitsForPartyLockAndUsesCommittedMembershipBeforeWritingReport() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            var leave = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                parties.findByIdForUpdate(primary.party()).orElseThrow();
                jdbc.update("update party_members set decision='LEFT' where id=?", primary.me()); locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return true;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var report = pool.submit(() -> service.create(student.getId(), primary.room(), input(primary.rep(), null, null), key()));
            try { assertThatThrownBy(() -> report.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            assertThat(leave.get(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> report.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(OwllyException.class)
                    .satisfies(e -> assertThat(((OwllyException) e.getCause()).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
        assertThat(reportCount(primary.room())).isZero(); assertThat(blockCount()).isZero();
    }

    @Test void actualCookieCsrfAndSwaggerSupportReportCreation() throws Exception {
        var initial = mvc.perform(get("/api/v1/parties/" + primary.party() + "/room").with(authentication(auth(student))))
                .andExpect(status().isOk()).andReturn();
        var cookie = initial.getResponse().getCookie("owlly_session"); assertThat(cookie).isNotNull();
        var session = mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk()).andReturn();
        String csrf = mapper.readTree(session.getResponse().getContentAsString()).at("/data/csrfToken").asText();
        mvc.perform(post(path(primary.room())).cookie(cookie).header("X-CSRF-Token", csrf).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input(primary.rep(), null, null))))
                .andExpect(status().isCreated());
        var doc = mapper.readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
        var operation = doc.path("paths").path("/api/v1/rooms/{roomId}/reports").path("post");
        assertThat(operation.path("operationId").asText()).isEqualTo("reportMember");
        assertThat(operation.path("parameters").toString()).contains("X-CSRF-Token", "Idempotency-Key");
    }

    private int purge(Instant now) { return new TransactionTemplate(transactions).execute(status -> reports.deleteExpiredEvidence(now)); }
    private long reportCount(Long room) { return jdbc.queryForObject("select count(*) from safety_reports where room_id=?", Long.class, room); }
    private long blockCount() { return jdbc.queryForObject("select count(*) from user_blocks where user_low_id=? and user_high_id=?", Long.class,
            Math.min(student.getId(), representative.getId()), Math.max(student.getId(), representative.getId())); }
    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    private String key() { return UUID.randomUUID().toString(); }
    private String path(Long room) { return "/api/v1/rooms/" + room + "/reports"; }
    private ReportCreate input(Long target, Long message, String details) {
        return new ReportCreate(target.toString(), "PRIVACY_REQUEST", message == null ? null : message.toString(), details);
    }
    private ResultActions postReport(User user, Long room, ReportCreate input, String key) throws Exception {
        return mvc.perform(post(path(room)).with(authentication(auth(user))).with(csrf().asHeader()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)));
    }
    private Long send(User user, Long room, String text) {
        return chat.send(user.getId(), room, new MessageCreate(key(), text), key(), Instant.now()).id();
    }
    private UsernamePasswordAuthenticationToken auth(User u) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(u.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
    }
    private User user(Long university) {
        var u = users.saveAndFlush(User.builder().universityId(university).email("report-test-" + key() + "@hufs.ac.kr").role(Role.STUDENT).status(UserStatus.ACTIVE).build());
        profiles.saveAndFlush(StudentProfile.builder().userId(u.getId()).gender(Gender.FEMALE).realNameEncrypted(cryptor.encrypt("신고테스트"))
                .sameGenderDefault(true).defaultDongId(dong.getId()).dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1")).build());
        return u;
    }
    private AdministrativeArea area(String name, AreaLevel level, Long parent) {
        return areas.saveAndFlush(AdministrativeArea.builder().officialCode("SR" + key().substring(0, 8)).name(name).level(level).parentId(parent).build());
    }
    private Fixture fixture() {
        var party = parties.saveAndFlush(RideParty.builder().universityId(student.getUniversityId()).pickupPointId(pickup.getId()).districtId(city.getId())
                .departureAt(departure).confirmDeadlineAt(departure.minusSeconds(900)).verificationCodeEncrypted(cryptor.encrypt("0042"))
                .routeVersion(1).status(PartyStatus.FORMING).build());
        var me = member(party.getId(), student); var rep = member(party.getId(), representative);
        party.assignRepresentative(rep.getId()); parties.saveAndFlush(party);
        Long room = chat.openRoom(party.getId());
        accounts.saveAndFlush(RoomAccount.builder().roomId(room).ownerMemberId(rep.getId()).purpose(AccountPurpose.PREPAY_RECEIVE)
                .bankCode("004").accountNumberEncrypted(cryptor.encrypt("12345678901")).holderNameEncrypted(cryptor.encrypt("신고테스트")).build());
        var f = new Fixture(party.getId(), room, me.getId(), rep.getId()); fixtures.add(f); return f;
    }
    private PartyMember member(Long partyId, User user) {
        var journey = journeys.saveAndFlush(Journey.builder().clientJourneyKey(UUID.randomUUID()).userId(user.getId()).universityId(user.getUniversityId())
                .pickupPointId(pickup.getId()).destinationDongId(dong.getId()).intendedDepartureAt(departure).outcome(JourneyOutcome.UNRESOLVED).build());
        journeyIds.add(journey.getId());
        var ride = rides.saveAndFlush(RideRequest.builder().clientRequestKey(UUID.randomUUID()).journeyId(journey.getId()).universityId(user.getUniversityId())
                .pickupPointId(pickup.getId()).districtId(city.getId()).destinationDongId(dong.getId()).departureAt(departure)
                .dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1"))
                .sameGenderOnly(true).genderSnapshot(Gender.FEMALE).status(RideRequestStatus.MATCHED).build());
        rideIds.add(ride.getId());
        return members.saveAndFlush(PartyMember.builder().partyId(partyId).requestId(ride.getId()).decision(MemberDecision.ACCEPTED)
                .boardingStatus(BoardingStatus.UNKNOWN).build());
    }
    private record Fixture(Long party, Long room, Long me, Long rep) {}
}
