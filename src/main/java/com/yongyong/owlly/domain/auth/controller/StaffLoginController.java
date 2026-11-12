package com.yongyong.owlly.domain.auth.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.auth.dto.SessionResponse;
import com.yongyong.owlly.domain.auth.dto.StaffLoginRequest;
import com.yongyong.owlly.domain.auth.service.SessionService;
import com.yongyong.owlly.domain.auth.service.StaffLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StaffLoginController {
    private final StaffLoginService logins;
    private final SessionService sessions;

    @PostMapping("/api/v1/auth/staff-sessions")
    public ResponseEntity<ApiResponse<SessionResponse>> login(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StaffLoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        IdempotencyKey.requireUuid(key);
        var result = logins.login(input, UUID.fromString(key).toString());
        var user = result.requireUser(); // 실패 기록 커밋이 끝난 뒤 오류 응답을 발생시킨다.
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.of(sessions.createSession(user, request, response)));
    }
}
