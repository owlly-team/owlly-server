package com.yongyong.owlly.domain.user.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.user.dto.StudentProfilePatch;
import com.yongyong.owlly.domain.user.dto.StudentProfileResponse;
import com.yongyong.owlly.domain.user.service.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/profile")
@RequiredArgsConstructor
public class StudentProfileController {
    private final StudentProfileService profiles;

    @GetMapping
    @Operation(operationId = "getMyProfile")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> get(@AuthenticationPrincipal SessionPrincipal principal) {
        return respond(profiles.getProfile(principal.userId()));
    }

    @PatchMapping
    @Operation(operationId = "patchMyProfile", parameters = @Parameter(name = "X-CSRF-Token", in = ParameterIn.HEADER, required = true,
            description = "로그인 또는 현재 세션 조회 응답의 data.csrfToken"))
    public ResponseEntity<ApiResponse<StudentProfileResponse>> update(@AuthenticationPrincipal SessionPrincipal principal,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody StudentProfilePatch input) {
        IdempotencyKey.requireUuid(key);
        return respond(profiles.updateProfile(principal.userId(), input, UUID.fromString(key).toString(), ifMatch));
    }

    private ResponseEntity<ApiResponse<StudentProfileResponse>> respond(StudentProfileResponse profile) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(ETags.of(profile)).body(ApiResponse.of(profile));
    }
}
