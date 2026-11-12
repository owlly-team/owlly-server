package com.yongyong.owlly.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.domain.auth.entity.StaffLoginReceipt;
import com.yongyong.owlly.domain.auth.repository.StaffLoginReceiptRepository;
import com.yongyong.owlly.domain.auth.service.StaffLoginCleanup;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 PostgreSQL 잠금·트랜잭션·BCrypt·JDBC 세션을 사용한다. 외부 API는 호출하지 않는다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false", "spring.session.timeout=30d",
        "owlly.verification.secret=01234567890123456789012345678901", "owlly.session.cookie-secure=true",
        "owlly.staff-login.max-failures=5", "owlly.staff-login.lock-seconds=1800", "owlly.staff-login.receipt-ttl-seconds=1800"})
@AutoConfigureMockMvc
class StaffLoginIntegrationTest {
    private static final String SECRET = "01234567890123456789012345678901";
    private static final String PASSWORD = "Staff-test-Password!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PasswordEncoder passwords;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired UniversityRepository universities;
    @Autowired StaffLoginCleanup loginCleanup;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean StaffLoginReceiptRepository receipts;
    final List<String> keys = new CopyOnWriteArrayList<>();
    final Set<String> accountKeys = ConcurrentHashMap.newKeySet();
    User staff;
    String email;

    @BeforeEach void setup() {
        email = "staff-login-test-" + UUID.randomUUID() + "@office.example";
        Long university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        staff = users.saveAndFlush(User.builder().universityId(university).email(email).role(Role.STAFF)
                .status(UserStatus.ACTIVE).passwordHash(passwords.encode(PASSWORD)).build());
    }

    @AfterEach void cleanupOnlyTestData() {
        for (String key : keys) jdbc.update("delete from staff_login_receipts where request_key=?", key);
        for (String key : accountKeys) jdbc.update("delete from staff_login_attempts where account_key=?", key);
        if (staff != null) {
            String id = staff.getId().toString();
            jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name=?)", id);
            jdbc.update("delete from spring_session where principal_name=?", id);
            jdbc.update("delete from users where id=?", staff.getId());
        }
    }

    @Test void issuedStaffWithoutStudentProfileCanLoginAndLogout() throws Exception {
        assertThat(profiles.existsById(staff.getId())).isFalse();
        var result = login(email, PASSWORD, key()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(staff.getId().toString()))
                .andExpect(jsonPath("$.data.user.role").value("STAFF"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.universityName").value("한국외국어대학교"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        Cookie cookie = cookieOf(result);
        assertThat(cookie.getMaxAge()).isEqualTo(2592000);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("STAFF"));
        mvc.perform(get("/api/v1/demand/candidates").cookie(cookie)).andExpect(status().isNotFound()); // 경로 자체는 후속 구현
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("X-CSRF-Token", csrf(result))
                        .header("Idempotency-Key", key())).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("owlly_session", 0));
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void fifthFailureLocksForThirtyMinutesAndCorrectPasswordCannotBypassIt() throws Exception {
        for (int i = 0; i < 4; i++) invalid(login(email, "wrong", key()));
        var fifth = login(email, "wrong", key()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(cookie().doesNotExist("owlly_session")).andReturn();
        assertThat(Long.parseLong(fifth.getResponse().getHeader("Retry-After"))).isBetween(1790L, 1800L);
        Instant blocked = blockedUntil();
        assertThat(blocked).isBetween(Instant.now().plusSeconds(1790), Instant.now().plusSeconds(1801));
        String blockedRequestKey = key();
        login(email, PASSWORD, blockedRequestKey).andExpect(status().isTooManyRequests());
        assertThat(failures()).isEqualTo(5);
        assertThat(blockedUntil()).isEqualTo(blocked);
        jdbc.update("update staff_login_attempts set blocked_until=? where account_key=?", Timestamp.from(Instant.now().minusSeconds(1)), accountKey(email));
        login(email, PASSWORD, blockedRequestKey).andExpect(status().isOk());
        assertThat(failures()).isZero();
        assertThat(blockedUntil()).isNull();
    }

    @Test void failedRetriesDoNotCountTwiceAndChangedPayloadConflicts() throws Exception {
        String requestKey = key();
        invalid(login(email, "wrong", requestKey));
        invalid(login("  " + email.toUpperCase(Locale.ROOT) + "  ", "wrong", requestKey.toUpperCase(Locale.ROOT)));
        assertThat(failures()).isEqualTo(1);
        login(email, "different", requestKey).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(failures()).isEqualTo(1);
        login(email, PASSWORD, key()).andExpect(status().isOk());
        assertThat(failures()).isZero();
        // 같은 실패 요청은 성공 뒤에도 실패 횟수를 다시 늘리지 않는다.
        invalid(login(email, "wrong", requestKey));
        assertThat(failures()).isZero();
    }

    @Test void missingEmailStudentAndIncorrectPasswordHaveSamePublicError() throws Exception {
        var wrong = invalid(login(email, "wrong", key())).andReturn();
        String absent = "absent-" + UUID.randomUUID() + "@office.example";
        var missing = invalid(login(absent, PASSWORD, key())).andReturn();
        jdbc.update("update users set role='STUDENT' where id=?", staff.getId());
        var student = invalid(login(email, PASSWORD, key())).andReturn();
        for (var response : List.of(wrong, missing, student)) {
            assertThat(mapper.readTree(response.getResponse().getContentAsString()).at("/error/message").asText())
                    .isEqualTo("메일이나 비밀번호가 맞지 않아요.");
            assertThat(response.getResponse().getCookie("owlly_session")).isNull();
        }
    }

    @Test void nonexistentEmailsAlsoReceiveSameLockPolicy() throws Exception {
        String absent = "absent-" + UUID.randomUUID() + "@office.example";
        for (int i = 0; i < 4; i++) invalid(login(absent, "wrong", key()));
        login(absent, "wrong", key()).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test void suspendedAndWithdrawnStatusesAreOnlyDisclosedAfterPasswordVerification() throws Exception {
        jdbc.update("update users set status='SUSPENDED' where id=?", staff.getId());
        invalid(login(email, "wrong", key()));
        login(email, PASSWORD, key()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        jdbc.update("update users set status='WITHDRAWN' where id=?", staff.getId());
        invalid(login(email, "wrong", key()));
        login(email, PASSWORD, key()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_UNAVAILABLE"));
    }

    @Test void successfulReplayRevalidatesPasswordAndDoesNotExtendReceiptExpiry() throws Exception {
        String requestKey = key();
        login(email, PASSWORD, requestKey).andExpect(status().isOk());
        Instant expiry = receipts.findById(requestKey).orElseThrow().getExpiresAt();
        login(email, PASSWORD, requestKey).andExpect(status().isOk());
        assertThat(receipts.findById(requestKey).orElseThrow().getExpiresAt()).isEqualTo(expiry);
        jdbc.update("update users set password_hash=? where id=?", passwords.encode("Changed-password!"), staff.getId());
        invalid(login(email, PASSWORD, requestKey));
        assertThat(failures()).isEqualTo(1);
        invalid(login(email, PASSWORD, requestKey));
        assertThat(failures()).isEqualTo(1);
        login(email, "Changed-password!", key()).andExpect(status().isOk());
    }

    @Test void successfulReplayCannotBypassNewLockOrChangedRoleStatusAndUniversity() throws Exception {
        String lockedKey = key();
        login(email, PASSWORD, lockedKey).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) invalid(login(email, "wrong", key()));
        login(email, "wrong", key()).andExpect(status().isTooManyRequests());
        login(email, PASSWORD, lockedKey).andExpect(status().isTooManyRequests());
        jdbc.update("update staff_login_attempts set blocked_until=? where account_key=?", Timestamp.from(Instant.now().minusSeconds(1)), accountKey(email));
        login(email, PASSWORD, lockedKey).andExpect(status().isOk());
        jdbc.update("update users set status='SUSPENDED' where id=?", staff.getId());
        login(email, PASSWORD, lockedKey).andExpect(status().isForbidden());
        jdbc.update("update users set status='ACTIVE' where id=?", staff.getId());
        String roleKey = key();
        login(email, PASSWORD, roleKey).andExpect(status().isOk());
        jdbc.update("update users set role='STUDENT' where id=?", staff.getId());
        invalid(login(email, PASSWORD, roleKey));
        jdbc.update("update users set role='STAFF' where id=?", staff.getId());
        String universityKey = key();
        login(email, PASSWORD, universityKey).andExpect(status().isOk());
        Long other = universities.findAll().stream().filter(u -> !u.getId().equals(staff.getUniversityId())).findFirst().orElseThrow().getId();
        jdbc.update("update users set university_id=? where id=?", other, staff.getId());
        login(email, PASSWORD, universityKey).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_UNAVAILABLE"));
        login(email, PASSWORD, universityKey).andExpect(status().isForbidden());
    }

    @Test void expiredKeyIsAuthenticatedAsNewRequestAndRecordDoesNotDuplicate() throws Exception {
        String requestKey = key();
        invalid(login(email, "wrong", requestKey));
        jdbc.update("update staff_login_receipts set expires_at=? where request_key=?", Timestamp.from(Instant.now().minusSeconds(1)), requestKey);
        login(email, PASSWORD, requestKey).andExpect(status().isOk());
        assertThat(receipts.findById(requestKey).orElseThrow().getUserId()).isEqualTo(staff.getId());
        assertThat(failures()).isZero();
    }

    @Test void rawCredentialsAreNotPersistedAndReceiptFailureRollsBackAttempt() throws Exception {
        String requestKey = key();
        doThrow(new IllegalStateException("simulated receipt failure")).when(receipts).saveAndFlush(any(StaffLoginReceipt.class));
        login(email, "wrong", requestKey).andExpect(status().isInternalServerError()).andExpect(cookie().doesNotExist("owlly_session"));
        assertThat(jdbc.queryForObject("select count(*) from staff_login_attempts where account_key=?", Integer.class, accountKey(email))).isZero();
        reset(receipts);
        invalid(login(email, "wrong", requestKey));
        var row = receipts.findById(requestKey).orElseThrow();
        assertThat(row.getRequestHash()).hasSize(64).doesNotContain(email, PASSWORD, "wrong");
        assertThat(failures()).isEqualTo(1);
    }

    @Test void cleanupSkipsExpiredReceiptInUseAndDeletesOtherExpiredReceipt() throws Exception {
        String inUse = key();
        String unused = key();
        invalid(login(email, "wrong", inUse));
        invalid(login(email, "wrong", unused));
        jdbc.update("update staff_login_receipts set expires_at=? where request_key in (?, ?)",
                Timestamp.from(Instant.now().minusSeconds(60)), inUse, unused);
        doAnswer(invocation -> {
            List<String> remaining = cleanupSnapshot(() -> jdbc.queryForList(
                    "select request_key from staff_login_receipts where request_key in (?, ?)", String.class, inUse, unused));
            assertThat(remaining).containsExactly(inUse);
            return invocation.callRealMethod();
        }).when(passwords).matches(eq(PASSWORD), anyString());

        // 기록 조회 후 비밀번호 확인 중 다른 트랜잭션이 정리해도 로그인은 성공해야 한다.
        login(email, PASSWORD, inUse).andExpect(status().isOk());
        assertThat(receipts.findById(inUse).orElseThrow().getUserId()).isEqualTo(staff.getId());
    }

    @Test void cleanupSkipsInactiveAttemptInUseAndDeletesOtherInactiveAttempt() throws Exception {
        login(email, PASSWORD, key()).andExpect(status().isOk());
        String inUse = accountKey(email);
        String unused = accountKey("cleanup-unused-" + UUID.randomUUID() + "@office.example");
        accountKeys.add(unused);
        Timestamp old = Timestamp.from(Instant.now().minusSeconds(31L * 86400));
        jdbc.update("update staff_login_attempts set last_seen_at=? where account_key=?", old, inUse);
        jdbc.update("insert into staff_login_attempts (account_key, failures, last_seen_at) values (?, 0, ?)", unused, old);
        doAnswer(invocation -> {
            List<String> remaining = cleanupSnapshot(() -> jdbc.queryForList(
                    "select account_key from staff_login_attempts where account_key in (?, ?)", String.class, inUse, unused));
            assertThat(remaining).containsExactly(inUse);
            return invocation.callRealMethod();
        }).when(passwords).matches(eq(PASSWORD), anyString());

        login(email, PASSWORD, key()).andExpect(status().isOk());
        assertThat(failures()).isZero();
    }

    @Test void cleanupPreservesValidReceiptsAndActiveLocksButDeletesEligibleRecords() throws Exception {
        String requestKey = key();
        login(email, PASSWORD, requestKey).andExpect(status().isOk());
        String accountKey = accountKey(email);
        jdbc.update("update staff_login_attempts set last_seen_at=?, blocked_until=? where account_key=?",
                Timestamp.from(Instant.now().minusSeconds(31L * 86400)), Timestamp.from(Instant.now().plusSeconds(1800)), accountKey);
        assertThat(cleanupSnapshot(() -> jdbc.queryForObject(
                "select count(*) from staff_login_attempts where account_key=?", Integer.class, accountKey))).isEqualTo(1);
        assertThat(cleanupSnapshot(() -> jdbc.queryForObject(
                "select count(*) from staff_login_receipts where request_key=?", Integer.class, requestKey))).isEqualTo(1);

        Timestamp expired = Timestamp.from(Instant.now().minusSeconds(60));
        jdbc.update("update staff_login_attempts set blocked_until=? where account_key=?", expired, accountKey);
        jdbc.update("update staff_login_receipts set expires_at=? where request_key=?", expired, requestKey);
        assertThat(cleanupSnapshot(() -> jdbc.queryForObject(
                "select count(*) from staff_login_attempts where account_key=?", Integer.class, accountKey))).isZero();
        assertThat(cleanupSnapshot(() -> jdbc.queryForObject(
                "select count(*) from staff_login_receipts where request_key=?", Integer.class, requestKey))).isZero();
    }

    @Test void concurrentRequestsCountEachNewFailureExactlyOnce() throws Exception {
        try (var pool = Executors.newFixedThreadPool(5)) {
            List<java.util.concurrent.Callable<Integer>> work = new ArrayList<>();
            for (int i = 0; i < 5; i++) work.add(() -> login(email, "wrong", key()).andReturn().getResponse().getStatus());
            var results = pool.invokeAll(work);
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get());
            assertThat(statuses).containsExactlyInAnyOrder(401, 401, 401, 401, 429);
        }
        assertThat(failures()).isEqualTo(5);
    }

    @Test void concurrentSameKeyCountsOnlyOneFailure() throws Exception {
        String requestKey = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(
                    (java.util.concurrent.Callable<Integer>) () -> login(email, "wrong", requestKey).andReturn().getResponse().getStatus(),
                    () -> login(email, "wrong", requestKey).andReturn().getResponse().getStatus()));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactly(401, 401);
        }
        assertThat(failures()).isEqualTo(1);
    }

    @Test void passwordWhitespaceIsPreservedAndMissingHashesFailSafely() throws Exception {
        String spaced = "  Actual-password!  ";
        jdbc.update("update users set password_hash=? where id=?", passwords.encode(spaced), staff.getId());
        invalid(login(email, spaced.strip(), key()));
        login(email, spaced, key()).andExpect(status().isOk());
        jdbc.update("update users set password_hash=null where id=?", staff.getId());
        invalid(login(email, PASSWORD, key()));
        jdbc.update("update users set password_hash='not-a-hash' where id=?", staff.getId());
        invalid(login(email, PASSWORD, key()));
    }

    @Test void malformedAndNonStringInputsAreValidationErrors() throws Exception {
        for (String field : List.of("email", "password")) {
            for (String value : List.of("123", "true", "false", "{}", "[]", "null")) {
                String body = field.equals("email") ? "{\"email\":" + value + ",\"password\":\"x\"}"
                        : "{\"email\":\"" + email + "\",\"password\":" + value + "}";
                raw(body, key(), null, null).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
            }
        }
        login("invalid-email", PASSWORD, key()).andExpect(status().isUnprocessableEntity());
        login(email, "", key()).andExpect(status().isUnprocessableEntity());
        login(email, "a".repeat(73), key()).andExpect(status().isUnprocessableEntity());
        login(email, "가".repeat(25), key()).andExpect(status().isUnprocessableEntity());
        raw("{\"email\":\"" + email + "\",\"password\":\"x\",\"role\":\"STAFF\"}", key(), null, null)
                .andExpect(status().isUnprocessableEntity());
        login(email, PASSWORD, "bad-key").andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/staff-sessions").header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from staff_login_attempts where account_key=?", Integer.class, accountKey(email))).isZero();
    }

    @Test void originCsrfAndSessionRotationAreEnforced() throws Exception {
        mvc.perform(post("/api/v1/auth/staff-sessions").header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/staff-sessions").header("Origin", "https://evil.example").header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD))).andExpect(status().isForbidden());
        String requestKey = key();
        var first = login(email, PASSWORD, requestKey).andExpect(status().isOk()).andReturn();
        raw(body(email, PASSWORD), requestKey, cookieOf(first), null).andExpect(status().isForbidden());
        var second = raw(body(email, PASSWORD), requestKey, cookieOf(first), csrf(first)).andExpect(status().isOk()).andReturn();
        assertThat(cookieOf(second).getValue()).isNotEqualTo(cookieOf(first).getValue());
        assertThat(csrf(second)).isNotEqualTo(csrf(first));
        mvc.perform(get("/api/v1/auth/session").cookie(cookieOf(first))).andExpect(status().isUnauthorized());
    }

    @Test void staffCannotAccessStudentDataChatOrCreateJourney() throws Exception {
        var loggedIn = login(email, PASSWORD, key()).andExpect(status().isOk()).andReturn();
        Cookie cookie = cookieOf(loggedIn);
        for (String path : List.of("/api/v1/me/profile", "/api/v1/me/journeys", "/api/v1/pickup-points", "/api/v1/banks",
                "/api/v1/journeys", "/api/v1/ride-requests/1", "/api/v1/parties/1", "/api/v1/rooms/1/messages", "/api/v1/transfers/1/recipient-account", "/api/v1/matching-slots")) {
            mvc.perform(get(path).cookie(cookie)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        mvc.perform(post("/api/v1/journeys").cookie(cookie).header("X-CSRF-Token", csrf(loggedIn))
                        .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/demand/candidates").with(user("student-test").roles("STUDENT"))).andExpect(status().isForbidden());
    }

    /** 실제 정리 SQL을 별도 연결에서 실행하되 롤백해 기존 로컬 데이터에는 영향을 주지 않는다. */
    private <T> T cleanupSnapshot(Supplier<T> snapshot) throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            return pool.submit(() -> new TransactionTemplate(transactions).execute(transaction -> {
                transaction.setRollbackOnly();
                jdbc.execute("set local statement_timeout = '3s'");
                loginCleanup.deleteExpired();
                return snapshot.get();
            })).get(8, TimeUnit.SECONDS);
        }
    }

    private String key() { String key = UUID.randomUUID().toString(); keys.add(key); return key; }
    private String accountKey(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(("staff-account:" + value.strip().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));
    }
    private int failures() throws Exception { return jdbc.queryForObject("select failures from staff_login_attempts where account_key=?", Integer.class, accountKey(email)); }
    private Instant blockedUntil() throws Exception {
        Timestamp timestamp = jdbc.queryForObject("select blocked_until from staff_login_attempts where account_key=?", Timestamp.class, accountKey(email));
        return timestamp == null ? null : timestamp.toInstant();
    }
    private String body(String email, String password) throws Exception { return mapper.writeValueAsString(Map.of("email", email, "password", password)); }
    private org.springframework.test.web.servlet.ResultActions login(String email, String password, String key) throws Exception {
        accountKeys.add(accountKey(email));
        return raw(body(email, password), key, null, null);
    }
    private org.springframework.test.web.servlet.ResultActions raw(String body, String key, Cookie cookie, String csrf) throws Exception {
        var request = post("/api/v1/auth/staff-sessions").header("Origin", "http://localhost:3000")
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body);
        if (cookie != null) request.cookie(cookie);
        if (csrf != null) request.header("X-CSRF-Token", csrf);
        return mvc.perform(request);
    }
    private org.springframework.test.web.servlet.ResultActions invalid(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }
    private Cookie cookieOf(MvcResult result) { return result.getResponse().getCookie("owlly_session"); }
    private String csrf(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()).at("/data/csrfToken").asText(); }
}
