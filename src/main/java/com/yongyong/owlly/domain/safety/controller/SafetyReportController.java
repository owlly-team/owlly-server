package com.yongyong.owlly.domain.safety.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.safety.dto.*;
import com.yongyong.owlly.domain.safety.service.SafetyReportService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/reports")
@RequiredArgsConstructor
public class SafetyReportController {
    private final SafetyReportService reports;

    @PostMapping
    @Operation(operationId = "reportMember", parameters = @Parameter(name = "X-CSRF-Token", in = ParameterIn.HEADER,
            required = true, description = "로그인 또는 현재 세션 조회 응답의 data.csrfToken"))
    public ResponseEntity<ApiResponse<SafetyReportResponse>> create(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long roomId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReportCreate input) {
        IdempotencyKey.requireUuid(key);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(reports.create(principal.userId(), roomId, input, UUID.fromString(key).toString())));
    }
}
