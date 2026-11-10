package com.yongyong.owlly.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.GlobalExceptionHandler;
import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.config.JacksonConfig;
import com.yongyong.owlly.config.SecurityConfig;
import com.yongyong.owlly.domain.auth.controller.SessionController;
import com.yongyong.owlly.domain.auth.dto.SessionResponse;
import com.yongyong.owlly.domain.auth.service.SessionService;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.university.entity.University;
import com.yongyong.owlly.domain.verification.controller.EmailVerificationController;
import com.yongyong.owlly.domain.verification.dto.EmailVerificationResponse;
import com.yongyong.owlly.domain.verification.service.EmailVerificationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;

/** 실제 Spring Session JDBC 필터를 거쳐 쿠키만으로 다음 요청의 인증을 복원한다. */
@SpringBootTest(classes = SessionIntegrationTest.TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sessions;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.session.jdbc.platform=h2",
        "spring.session.jdbc.initialize-schema=always", "spring.session.timeout=60s",
        "owlly.session.cookie-secure=true"
})
@AutoConfigureMockMvc
class SessionIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class, JacksonConfig.class, GlobalExceptionHandler.class,
            SessionController.class, SessionService.class, TestEndpoints.class, EmailVerificationController.class})
    static class TestApp {}

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserRepository users;
    @MockitoBean UniversityRepository universities;
    @MockitoBean EmailVerificationService verifications;
    User user;

    @BeforeEach
    void setup() {
        jdbc.update("delete from SPRING_SESSION_ATTRIBUTES");
        jdbc.update("delete from SPRING_SESSION");
        user = User.builder().universityId(1L).email("student@hufs.ac.kr")
                .role(Role.STUDENT).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", 101L);
        when(users.findById(101L)).thenReturn(Optional.of(user));
        when(universities.findById(1L)).thenReturn(Optional.of(University.builder().name("한국외국어대학교").active(true).build()));
    }

    @Test
    void cookieRestoresJdbcSessionAndResponseHidesSessionId() throws Exception {
        MvcResult login = login(null);
        Cookie cookie = cookie(login);
        assertThat(login.getResponse().getHeaders("Set-Cookie").toString())
                .contains("owlly_session=", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(jdbc.queryForObject("select count(*) from SPRING_SESSION", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/auth/session").cookie(cookie))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.user.id").value("101"))
                .andExpect(jsonPath("$.data.user.universityName").value("한국외국어대학교"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionId").doesNotExist());
        mvc.perform(get("/api/v1/me/session-test").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/staff/session-test").cookie(cookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void anonymousAndExpiredSessionsReturnJson401() throws Exception {
        mvc.perform(get("/api/v1/auth/session")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        Cookie cookie = cookie(login(null));
        jdbc.update("update SPRING_SESSION set LAST_ACCESS_TIME = 0, EXPIRY_TIME = 0");
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfProtectsMutationsAndLogoutInvalidatesStoredSession() throws Exception {
        MvcResult login = login(null);
        Cookie cookie = cookie(login);
        String csrf = token(login);
        String key = UUID.randomUUID().toString();
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("Idempotency-Key", key))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("Idempotency-Key", key)
                        .header("X-CSRF-Token", "incorrect"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("Idempotency-Key", key)
                        .header("X-CSRF-Token", csrf))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().maxAge("owlly_session", 0));
        assertThat(jdbc.queryForObject("select count(*) from SPRING_SESSION", Integer.class)).isZero();
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie).header("Idempotency-Key", key)
                        .header("X-CSRF-Token", csrf)).andExpect(status().isNoContent());
    }

    @Test
    void logoutRequiresCanonicalUuid() throws Exception {
        mvc.perform(delete("/api/v1/auth/session")).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/v1/auth/session").header("Idempotency-Key", "1-1-1-1-1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginRotatesSessionIdAndCsrfToken() throws Exception {
        MvcResult first = login(null);
        MvcResult second = mvc.perform(post("/api/v1/auth/student-sessions").cookie(cookie(first))
                        .header("Origin", "http://localhost:3000").header("X-CSRF-Token", token(first)))
                .andExpect(status().isOk()).andReturn();
        assertThat(cookie(second).getValue()).isNotEqualTo(cookie(first).getValue());
        assertThat(token(second)).isNotEqualTo(token(first));
        mvc.perform(get("/api/v1/auth/session").cookie(cookie(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/session").cookie(cookie(second))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/auth/session").cookie(cookie(second))
                        .header("X-CSRF-Token", token(first)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountChangesApplyToExistingSessions() throws Exception {
        Cookie cookie = cookie(login(null));
        ReflectionTestUtils.setField(user, "role", Role.STAFF);
        mvc.perform(get("/api/v1/me/session-test").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/staff/session-test").cookie(cookie)).andExpect(status().isOk());
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        mvc.perform(get("/api/v1/staff/session-test").cookie(cookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk());
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);
        mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from SPRING_SESSION", Integer.class)).isZero();
    }

    @Test
    void suspendedAccountErrorIncludesCorsHeadersForAllowedFrontend() throws Exception {
        Cookie cookie = cookie(login(null));
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);

        mvc.perform(get("/api/v1/me/session-test").cookie(cookie)
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void withdrawnAccountErrorIncludesCorsHeadersAndInvalidatesSession() throws Exception {
        Cookie cookie = cookie(login(null));
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);

        mvc.perform(get("/api/v1/auth/session").cookie(cookie)
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(jdbc.queryForObject("select count(*) from SPRING_SESSION", Integer.class)).isZero();
    }

    @Test
    void publicVerificationNeedsAllowedOriginButNoCsrf() throws Exception {
        String key = UUID.randomUUID().toString();
        when(verifications.issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(key)))
                .thenReturn(new EmailVerificationResponse(1L, Instant.now().plusSeconds(600), Instant.now()));
        var body = "{\"universityId\":\"1\",\"email\":\"student@hufs.ac.kr\"}";
        mvc.perform(post("/api/v1/auth/email-verifications").header("Origin", "http://localhost:3000")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/email-verifications").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/email-verifications").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(options("/api/v1/auth/session").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "X-CSRF-Token,Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    private MvcResult login(Cookie cookie) throws Exception {
        var request = post("/api/v1/auth/student-sessions").header("Origin", "http://localhost:3000");
        if (cookie != null) request.cookie(cookie);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private Cookie cookie(MvcResult result) {
        return result.getResponse().getCookie("owlly_session");
    }

    private String token(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/csrfToken").asText();
    }

    /** 테스트 컨텍스트에서만 존재. 실제 가입·로그인 API는 후속 브랜치에서 구현한다. */
    @RestController
    static class TestEndpoints {
        private final SessionService sessions;
        private final UserRepository users;
        TestEndpoints(SessionService sessions, UserRepository users) { this.sessions = sessions; this.users = users; }

        @PostMapping("/api/v1/auth/student-sessions")
        ApiResponse<SessionResponse> login(HttpServletRequest request, HttpServletResponse response) {
            return ApiResponse.of(sessions.createSession(users.findById(101L).orElseThrow(), request, response));
        }

        @GetMapping({"/api/v1/me/session-test", "/api/v1/staff/session-test"})
        String protectedResource() { return "ok"; }
    }
}
