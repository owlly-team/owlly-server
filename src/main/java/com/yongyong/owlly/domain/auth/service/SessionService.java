package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.auth.dto.SessionResponse;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserRepository users;
    private final UniversityRepository universities;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokens;
    private final CookieSerializer cookieSerializer;

    /** 자격 검증과 가입/증표 소비 트랜잭션이 성공한 뒤에만 호출한다. 공개 로그인 API가 아니다. */
    public SessionResponse createSession(User authenticatedUser, HttpServletRequest request, HttpServletResponse response) {
        if (authenticatedUser.getStatus() != UserStatus.ACTIVE) {
            throw new OwllyException(authenticatedUser.getStatus() == UserStatus.SUSPENDED
                    ? ErrorCode.ACCOUNT_SUSPENDED : ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new SessionPrincipal(authenticatedUser.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.getRole().name())));
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        // 로그인 이전 CSRF 토큰을 교체하고 응답에는 새 토큰만 제공한다.
        var csrf = csrfTokens.generateToken(request);
        csrfTokens.saveToken(csrf, request, response);
        return describe(authenticatedUser, request, csrf.getToken());
    }

    public SessionResponse getSession(SessionPrincipal principal, HttpServletRequest request, String csrfToken) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new OwllyException(ErrorCode.UNAUTHENTICATED));
        return describe(user, request, csrfToken);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, ""));
    }

    private SessionResponse describe(User user, HttpServletRequest request, String csrfToken) {
        var session = request.getSession(false);
        if (session == null) throw new OwllyException(ErrorCode.UNAUTHENTICATED);
        Instant expiresAt = Instant.ofEpochMilli(session.getLastAccessedTime()).plusSeconds(session.getMaxInactiveInterval());
        String universityName = universities.findById(user.getUniversityId())
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND)).getName();
        return new SessionResponse(SessionResponse.CurrentUser.from(user, universityName), expiresAt, csrfToken);
    }
}
