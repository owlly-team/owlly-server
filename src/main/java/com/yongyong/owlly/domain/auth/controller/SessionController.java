package com.yongyong.owlly.domain.auth.controller;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.auth.dto.SessionResponse;
import com.yongyong.owlly.domain.auth.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/session")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessions;

    @GetMapping
    public ResponseEntity<ApiResponse<SessionResponse>> get(@AuthenticationPrincipal SessionPrincipal principal,
                                                           HttpServletRequest request, CsrfToken csrfToken) {
        if (principal == null) throw new OwllyException(ErrorCode.UNAUTHENTICATED);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.of(sessions.getSession(principal, request, csrfToken.getToken())));
    }

    /** 세션이 이미 없거나 만료된 경우도 204. 동일 로그아웃 재시도의 효과가 같다. */
    @DeleteMapping
    public ResponseEntity<Void> logout(@RequestHeader("Idempotency-Key") String key,
                                       HttpServletRequest request, HttpServletResponse response) {
        try {
            if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR);
        }
        sessions.logout(request, response);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}
