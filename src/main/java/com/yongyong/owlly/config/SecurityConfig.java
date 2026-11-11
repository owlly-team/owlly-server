package com.yongyong.owlly.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.security.SecurityErrorWriter;
import com.yongyong.owlly.common.security.SessionAccountFilter;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final List<String> SAFE_METHODS = List.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-Token");
        return repository;
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    public CookieSerializer cookieSerializer(@Value("${owlly.session.cookie-secure:true}") boolean secure,
                                            @Value("${spring.session.timeout:30d}") String timeout) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("owlly_session");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        // 서버 유휴 만료와 동일한 기간의 지속 쿠키. 설정을 바꾸면 쿠키 수명도 함께 바뀐다.
        int maxAge = Math.toIntExact(DurationStyle.detectAndParse(timeout).toSeconds());
        if (maxAge <= 0) throw new IllegalArgumentException("세션 만료 기간은 양수여야 합니다.");
        serializer.setCookieMaxAge(maxAge);
        return serializer;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper mapper, UserRepository users,
                                           SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
                                           @Value("${owlly.security.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
                                           List<String> allowedOrigins) throws Exception {
        var errors = new SecurityErrorWriter(mapper);
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "Idempotency-Key", "If-Match"));
        cors.setExposedHeaders(List.of("ETag", "Retry-After"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        var corsFilter = new CorsFilter(source);
        corsFilter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) throws IOException {
                errors.write(((org.springframework.http.server.ServletServerHttpResponse) response).getServletResponse(),
                        ErrorCode.FORBIDDEN);
            }
        });
        var originFilter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                // 공개 인증 POST는 CSRF 토큰 대신 명시적인 Origin 허용 목록으로 보호한다.
                if (request.getMethod().equals("POST")
                        && request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/v1/auth/")
                        && cors.checkOrigin(request.getHeader("Origin")) == null) {
                    errors.write(response, ErrorCode.FORBIDDEN);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        http
                .cors(AbstractHttpConfigurer::disable)
                // CORS → Origin → 계정 상태 → CSRF 순서로 실행해 오류 응답에도 CORS 헤더를 붙인다.
                .addFilterAt(corsFilter, CorsFilter.class)
                .addFilterAfter(originFilter, CorsFilter.class)
                .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(c -> c.csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            return !SAFE_METHODS.contains(request.getMethod()) && authentication != null
                                    && authentication.isAuthenticated()
                                    && !(authentication instanceof AnonymousAuthenticationToken);
                        }))
                .addFilterBefore(new SessionAccountFilter(users, errors), CsrfFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> errors.write(response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, ex) -> errors.write(response, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/universities", "/api/v1/profile-options",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/email-verifications",
                                "/api/v1/auth/email-verifications/*/verify", "/api/v1/auth/student-registrations",
                                "/api/v1/auth/student-sessions", "/api/v1/auth/staff-sessions").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/session").permitAll()
                        .requestMatchers("/api/v1/pickup-points", "/api/v1/banks", "/api/v1/me/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/staff/**", "/api/v1/demand/**").hasRole("STAFF")
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
