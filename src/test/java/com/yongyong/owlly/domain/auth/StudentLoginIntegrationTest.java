package com.yongyong.owlly.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.domain.auth.entity.StudentLoginReceipt;
import com.yongyong.owlly.domain.auth.repository.StudentLoginReceiptRepository;
import com.yongyong.owlly.domain.auth.service.LoginReceiptCleanup;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import com.yongyong.owlly.domain.verification.entity.EmailVerification;
import com.yongyong.owlly.domain.verification.repository.EmailVerificationRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 실제 PostgreSQL 트랜잭션·잠금·세션으로 검증. 외부 메일과 위치 호출만 대체한다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false", "spring.session.timeout=30d", "management.health.mail.enabled=false",
        "owlly.session.cookie-secure=true", "owlly.verification.mail-from=login-test@example.com",
        "owlly.verification.secret=01234567890123456789012345678901"})
@AutoConfigureMockMvc
class StudentLoginIntegrationTest {
    private static final String SECRET = "01234567890123456789012345678901";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired UniversityRepository universities;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired EmailVerificationRepository verifications;
    @Autowired TextCryptor cryptor;
    @Autowired LoginReceiptCleanup cleanup;
    @MockitoSpyBean StudentLoginReceiptRepository receipts;
    @MockitoBean JavaMailSender mail;
    @MockitoBean DropoffLocationService locations;
    String email;
    String token;
    Long universityId;
    Long verificationId;
    User user;
    AdministrativeArea dong;

    @BeforeEach void setup() throws Exception {
        universityId = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        email = "login-test-" + UUID.randomUUID() + "@hufs.ac.kr";
        token = UUID.randomUUID().toString();
        dong = areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode("99" + String.format("%08d", new java.security.SecureRandom().nextInt(100_000_000)))
                .name("로그인테스트동").level(AreaLevel.DONG).build());
        user = users.saveAndFlush(User.builder().universityId(universityId).email(email)
                .role(Role.STUDENT).status(UserStatus.ACTIVE).emailVerifiedAt(Instant.now()).build());
        profiles.saveAndFlush(StudentProfile.builder().userId(user.getId()).gender(Gender.FEMALE).sameGenderDefault(false)
                .realNameEncrypted(cryptor.encrypt("테스트학생")).defaultDongId(dong.getId())
                .dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1")).build());
        verificationId = proof(token, universityId);
    }

    private Long proof(String proofToken, Long schoolId) throws Exception {
        var v = EmailVerification.builder().universityId(schoolId).email(email).codeHash("test-only")
                .sentAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(("grant:" + proofToken).getBytes(StandardCharsets.UTF_8)));
        v.verify(UUID.randomUUID().toString(), hash, Instant.now(), Instant.now().plusSeconds(600));
        return verifications.saveAndFlush(v).getId();
    }

    @AfterEach void removeOnlyTestData() {
        if (user != null) {
            String id = user.getId().toString();
            jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name = ?)", id);
            jdbc.update("delete from spring_session where principal_name = ?", id);
            jdbc.update("delete from student_login_receipts where user_id = ?", user.getId());
            jdbc.update("delete from student_registration_receipts where user_id = ?", user.getId());
            jdbc.update("delete from student_profiles where user_id = ?", user.getId());
            jdbc.update("delete from users where id = ?", user.getId());
        }
        if (email != null) {
            jdbc.update("delete from email_verifications where email = ?", email);
            jdbc.update("delete from verification_attempt_limits where email = ?", email);
        }
        if (dong != null) areas.deleteById(dong.getId());
    }

    @Test void loginRestoresExistingProfileAndCreatesPersistentSession() throws Exception {
        String encryptedName = profiles.findById(user.getId()).orElseThrow().getRealNameEncrypted();
        MvcResult result = login(token, UUID.randomUUID().toString()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.user.universityName").value("한국외국어대학교"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty()).andReturn();
        Cookie cookie = result.getResponse().getCookie("owlly_session");
        assertThat(cookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(jdbc.queryForObject("select max_inactive_interval from spring_session where principal_name = ?", Integer.class, user.getId().toString()))
                .isEqualTo(30 * 24 * 60 * 60);
        assertThat(Instant.parse(mapper.readTree(result.getResponse().getContentAsString()).at("/data/expiresAt").asText()))
                .isBetween(Instant.now().plusSeconds(2591900), Instant.now().plusSeconds(2592100));
        assertThat(profiles.findById(user.getId()).orElseThrow().getRealNameEncrypted()).isEqualTo(encryptedName);
        assertThat(profiles.findById(user.getId()).orElseThrow().isSameGenderDefault()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/pickup-points").cookie(cookie)).andExpect(status().isOk());
        String csrf = csrf(result);
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("X-CSRF-Token", csrf)
                        .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("owlly_session", 0));
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isUnauthorized());
        verifyNoInteractions(locations);
    }

    @Test void emailIssueVerifyAndStudentLoginUseSameProofContract() throws Exception {
        // setup의 합성 증표가 발송 간격 제한에 걸리지 않도록 지난 발송으로 둔다.
        jdbc.update("update email_verifications set sent_at=? where id=?", Timestamp.from(Instant.now().minusSeconds(61)), verificationId);
        var issued = mvc.perform(post("/api/v1/auth/email-verifications").header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("universityId", universityId.toString(), "email", email))))
                .andExpect(status().isAccepted()).andReturn();
        String id = mapper.readTree(issued.getResponse().getContentAsString()).at("/data/verificationId").asText();
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        var code = Pattern.compile("\\d{6}").matcher(message.getValue().getText());
        assertThat(code.find()).isTrue();
        var verified = mvc.perform(post("/api/v1/auth/email-verifications/" + id + "/verify")
                        .header("Origin", "http://localhost:3000").header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.group() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String grant = mapper.readTree(verified.getResponse().getContentAsString()).at("/data/verificationToken").asText();
        login(grant, UUID.randomUUID().toString()).andExpect(status().isOk());
    }

    @Test void missingAccountDoesNotConsumeProofAndCanContinueRegistration() throws Exception {
        profiles.deleteById(user.getId());
        users.deleteById(user.getId());
        login(token, UUID.randomUUID().toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(cookie().doesNotExist("owlly_session"));
        unconsumed();
        when(locations.resolveDong(anyDouble(), anyDouble())).thenReturn(dong);
        var result = mvc.perform(post("/api/v1/auth/student-registrations").header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("verificationToken", token, "profile", java.util.Map.of(
                                "realName", "새학생", "genderCode", "FEMALE", "defaultDropoff", java.util.Map.of("latitude", 37.3, "longitude", 127.1))))))
                .andExpect(status().isCreated()).andReturn();
        user = users.findById(mapper.readTree(result.getResponse().getContentAsString()).at("/data/user/id").asLong()).orElseThrow();
        login(token, UUID.randomUUID().toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERIFICATION_USED"));
    }

    @Test void staffWithdrawnAndSuspendedAccountsAreRejectedWithoutConsumingProof() throws Exception {
        jdbc.update("update users set role='STAFF' where id=?", user.getId());
        rejected("ACCOUNT_UNAVAILABLE");
        jdbc.update("update users set role='STUDENT', status='WITHDRAWN' where id=?", user.getId());
        rejected("ACCOUNT_UNAVAILABLE");
        jdbc.update("update users set status='SUSPENDED' where id=?", user.getId());
        rejected("ACCOUNT_SUSPENDED");
        unconsumed();
    }

    @Test void universityMismatchAndMissingProfileCannotLogin() throws Exception {
        Long otherSchool = universities.findAll().stream().filter(u -> !u.getId().equals(universityId)).findFirst().orElseThrow().getId();
        jdbc.update("update email_verifications set university_id=? where id=?", otherSchool, verificationId);
        rejected("ACCOUNT_UNAVAILABLE");
        jdbc.update("update email_verifications set university_id=? where id=?", universityId, verificationId);
        profiles.deleteById(user.getId());
        rejected("ACCOUNT_UNAVAILABLE");
        unconsumed();
    }

    @Test void retriesReuseResultButCannotChangePayloadOrReuseProofUnderAnotherKey() throws Exception {
        String key = UUID.randomUUID().toString();
        login(token, key).andExpect(status().isOk());
        login(token, key.toUpperCase(java.util.Locale.ROOT)).andExpect(status().isOk());
        login("different-proof", key).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        login(token, UUID.randomUUID().toString()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERIFICATION_USED"));
        assertThat(receipts.findById(key).orElseThrow().getRequestHash()).doesNotContain(token);
    }

    @Test void replayRechecksAccountStatusAndExpiry() throws Exception {
        String key = UUID.randomUUID().toString();
        login(token, key).andExpect(status().isOk());
        jdbc.update("update users set status='SUSPENDED' where id=?", user.getId());
        login(token, key).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        jdbc.update("update users set status='ACTIVE', role='STAFF' where id=?", user.getId());
        login(token, key).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_UNAVAILABLE"));
        jdbc.update("update users set role='STUDENT', status='WITHDRAWN' where id=?", user.getId());
        login(token, key).andExpect(status().isForbidden());
        jdbc.update("update users set status='ACTIVE' where id=?", user.getId());
        jdbc.update("update student_login_receipts set expires_at=? where request_key=?", Timestamp.from(Instant.now().minusSeconds(1)), key);
        login(token, key).andExpect(status().isGone());
        cleanup.deleteExpired();
        assertThat(receipts.existsById(key)).isFalse();
        login(token, key).andExpect(status().isConflict());
    }

    @Test void receiptFailureRollsBackProofAndCanBeRetried() throws Exception {
        String key = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("simulated receipt failure")).when(receipts).saveAndFlush(any(StudentLoginReceipt.class));
        login(token, key).andExpect(status().isInternalServerError()).andExpect(cookie().doesNotExist("owlly_session"));
        unconsumed();
        assertThat(receipts.existsById(key)).isFalse();
        reset(receipts);
        login(token, key).andExpect(status().isOk());
    }

    @Test void expiredUnknownProofAndInvalidInputDoNotCreateSessions() throws Exception {
        jdbc.update("update email_verifications set grant_expires_at=? where id=?", Timestamp.from(Instant.now().minusSeconds(1)), verificationId);
        login(token, UUID.randomUUID().toString()).andExpect(status().isGone()).andExpect(cookie().doesNotExist("owlly_session"));
        login("unknown", UUID.randomUUID().toString()).andExpect(status().isConflict());
        login(token, "bad").andExpect(status().isUnprocessableEntity());
        login(" ", UUID.randomUUID().toString()).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/student-sessions").header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verificationToken\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/student-sessions").header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"x\",\"role\":\"STAFF\"}"))
                .andExpect(status().isUnprocessableEntity());
        unconsumed();
    }

    @Test void nonStringProofsAreRejectedBeforeLogin() throws Exception {
        for (String value : List.of("123", "123.4", "true", "false", "{}", "[]", "null")) {
            String key = UUID.randomUUID().toString();
            mvc.perform(post("/api/v1/auth/student-sessions").header("Origin", "http://localhost:3000")
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"verificationToken\":" + value + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0]").value(org.hamcrest.Matchers.startsWith("verificationToken:")))
                    .andExpect(cookie().doesNotExist("owlly_session"));
            assertThat(receipts.existsById(key)).isFalse();
        }
        unconsumed();
        login(token, UUID.randomUUID().toString()).andExpect(status().isOk());
    }

    @Test void originAndCsrfAreRequiredInTheAppropriateContexts() throws Exception {
        mvc.perform(post("/api/v1/auth/student-sessions").header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body(token))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/student-sessions").header("Origin", "https://evil.example")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().isForbidden());
        unconsumed();
        String key = UUID.randomUUID().toString();
        var first = login(token, key).andExpect(status().isOk()).andReturn();
        Cookie oldCookie = first.getResponse().getCookie("owlly_session");
        mvc.perform(post("/api/v1/auth/student-sessions").cookie(oldCookie).header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().isForbidden());
        var second = mvc.perform(post("/api/v1/auth/student-sessions").cookie(oldCookie).header("Origin", "http://localhost:3000")
                        .header("X-CSRF-Token", csrf(first)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().isOk()).andReturn();
        assertThat(second.getResponse().getCookie("owlly_session").getValue()).isNotEqualTo(oldCookie.getValue());
        assertThat(csrf(second)).isNotEqualTo(csrf(first));
        mvc.perform(get("/api/v1/auth/session").cookie(oldCookie)).andExpect(status().isUnauthorized());
    }

    @Test void concurrentSameKeyReplaysAndDifferentKeysCannotConsumeTwice() throws Exception {
        String key = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(
                    (java.util.concurrent.Callable<Integer>) () -> login(token, key).andReturn().getResponse().getStatus(),
                    () -> login(token, key).andReturn().getResponse().getStatus()));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactly(200, 200);
            String nextToken = UUID.randomUUID().toString();
            proof(nextToken, universityId);
            var different = pool.invokeAll(List.of(
                    (java.util.concurrent.Callable<Integer>) () -> login(nextToken, UUID.randomUUID().toString()).andReturn().getResponse().getStatus(),
                    () -> login(nextToken, UUID.randomUUID().toString()).andReturn().getResponse().getStatus()));
            assertThat(List.of(different.get(0).get(), different.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from users where email=?", Integer.class, email)).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions login(String grant, String key) throws Exception {
        return mvc.perform(post("/api/v1/auth/student-sessions").header("Origin", "http://localhost:3000")
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(grant)));
    }

    private String body(String grant) throws Exception { return mapper.writeValueAsString(java.util.Map.of("verificationToken", grant)); }
    private String csrf(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()).at("/data/csrfToken").asText(); }
    private void unconsumed() { assertThat(verifications.findById(verificationId).orElseThrow().getGrantConsumedAt()).isNull(); }
    private void rejected(String code) throws Exception {
        login(token, UUID.randomUUID().toString()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(cookie().doesNotExist("owlly_session"));
    }
}
