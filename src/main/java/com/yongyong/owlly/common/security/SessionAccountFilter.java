package com.yongyong.owlly.common.security;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final SecurityErrorWriter errors;

    public SessionAccountFilter(UserRepository users, SecurityErrorWriter errors) {
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            User user = users.findById(principal.userId()).orElse(null);
            if (user == null || user.getStatus() == UserStatus.WITHDRAWN) {
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                errors.write(response, ErrorCode.UNAUTHENTICATED);
                return;
            }
            // 정지 계정의 정산 예외 권한은 정책 확정 전까지 부여하지 않는다.
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (user.getStatus() == UserStatus.SUSPENDED
                    && !(path.equals("/api/v1/auth/session")
                    && (request.getMethod().equals("GET") || request.getMethod().equals("DELETE")))) {
                errors.write(response, ErrorCode.ACCOUNT_SUSPENDED);
                return;
            }
            var current = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(current);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
