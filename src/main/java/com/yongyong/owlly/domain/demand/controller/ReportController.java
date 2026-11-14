package com.yongyong.owlly.domain.demand.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.demand.dto.StakeholderReportCreate;
import com.yongyong.owlly.domain.demand.dto.StakeholderReportResponse;
import com.yongyong.owlly.domain.demand.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관계자 리포트 API (API-46 생성, API-47 목록, API-48 상세). 스태프 전용(경로 보안).
 *
 * <p>파일 생성·다운로드(API-49·50)는 범위 밖이라 status 는 DRAFT 에서 멈추고 downloadAvailable 은 false 다.
 */
@RestController
@RequestMapping("/api/v1/staff/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * API-46 후보·조치로 리포트 초안 생성. Idempotency-Key 는 UUID 형식만 검증한다
     * (명세는 키 필수만 요구하며, 리포트 초안은 멱등 저장소 없이 형식만 확인한다 — {@link IdempotencyKey} 참고).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StakeholderReportResponse>> create(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StakeholderReportCreate request) {
        IdempotencyKey.requireUuid(idempotencyKey);
        StakeholderReportResponse report = reportService.create(principal.userId(), request);
        return ResponseEntity.status(201).body(ApiResponse.of(report));
    }

    /** API-47 내 리포트 목록. 최신순, 다음 페이지는 meta.nextCursor 로. */
    @GetMapping
    public ApiResponse<java.util.List<StakeholderReportResponse>> list(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        ReportService.ListResult result = reportService.list(principal.userId(), cursor, limit);
        return ApiResponse.of(result.reports(), new ApiResponse.PageMeta(result.nextCursor(), result.hasMore()));
    }

    /** API-48 리포트 미리보기·생성 상태 조회. 내가 만든 리포트만(아니면 404). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StakeholderReportResponse>> get(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long id) {
        StakeholderReportResponse report = reportService.get(principal.userId(), id);
        return ResponseEntity.ok().eTag(ETags.of(report)).body(ApiResponse.of(report));
    }
}
