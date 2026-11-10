package com.yongyong.owlly.domain.auth.controller;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.domain.auth.dto.SessionResponse;
import com.yongyong.owlly.domain.auth.dto.StudentRegistrationRequest;
import com.yongyong.owlly.domain.auth.service.SessionService;
import com.yongyong.owlly.domain.auth.service.StudentRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StudentRegistrationController {
    private final StudentRegistrationService registrations;
    private final SessionService sessions;

    @PostMapping("/api/v1/auth/student-registrations")
    public ResponseEntity<ApiResponse<SessionResponse>> register(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StudentRegistrationRequest input, HttpServletRequest request, HttpServletResponse response) {
        String canonicalKey;
        try {
            canonicalKey = UUID.fromString(key).toString();
            if (!canonicalKey.equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new OwllyException(ErrorCode.VALIDATION_ERROR); }
        var user = registrations.register(input, canonicalKey); // 프록시 트랜잭션 커밋이 끝난 뒤에만 세션 생성
        var session = sessions.createSession(user, request, response);
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(ApiResponse.of(session));
    }
}
