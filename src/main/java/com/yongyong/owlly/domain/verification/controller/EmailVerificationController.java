package com.yongyong.owlly.domain.verification.controller;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.domain.verification.dto.*;
import com.yongyong.owlly.domain.verification.service.EmailVerificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService service;

    @PostMapping
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> issue(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(service.issue(request, uuid(key))));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<VerificationGrantResponse>> verify(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CodeVerificationRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.of(service.verify(id, request.code(), uuid(key))));
    }

    private String uuid(String key) {
        try {
            UUID parsed = UUID.fromString(key);
            if (!parsed.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
            return parsed.toString();
        } catch (IllegalArgumentException e) { throw new OwllyException(ErrorCode.VALIDATION_ERROR); }
    }
}
