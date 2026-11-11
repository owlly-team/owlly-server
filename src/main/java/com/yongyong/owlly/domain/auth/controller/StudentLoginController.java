package com.yongyong.owlly.domain.auth.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.auth.dto.SessionResponse;
import com.yongyong.owlly.domain.auth.dto.StudentLoginRequest;
import com.yongyong.owlly.domain.auth.service.SessionService;
import com.yongyong.owlly.domain.auth.service.StudentLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StudentLoginController {
    private final StudentLoginService logins;
    private final SessionService sessions;

    @PostMapping("/api/v1/auth/student-sessions")
    public ResponseEntity<ApiResponse<SessionResponse>> login(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StudentLoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        IdempotencyKey.requireUuid(key);
        // 증표 소비와 재시도 기록이 커밋된 뒤에만 세션을 발급한다.
        var user = logins.login(input, UUID.fromString(key).toString());
        var session = sessions.createSession(user, request, response);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.of(session));
    }
}
