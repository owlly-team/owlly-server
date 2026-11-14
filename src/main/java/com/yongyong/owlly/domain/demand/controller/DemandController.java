package com.yongyong.owlly.domain.demand.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.demand.dto.Contribution;
import com.yongyong.owlly.domain.demand.dto.DemandCandidate;
import com.yongyong.owlly.domain.demand.dto.DemandCandidatePage;
import com.yongyong.owlly.domain.demand.service.DemandContributionService;
import com.yongyong.owlly.domain.demand.service.DemandQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수요 지도·노선 후보 API (API-43·44 로그인 사용자, API-45 학생용).
 *
 * <p>추정 수요·후보 순위·근거 지표는 AI 수요 산출(run)의 결과를 읽어 보여 줄 뿐이다.
 * 조회 전용이라 Idempotency-Key 는 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DemandController {

    private final DemandQueryService demandQueryService;
    private final DemandContributionService demandContributionService;

    /**
     * API-43 수요 지도·노선 후보 목록. 로그인 사용자. 후보 순위순, 표본 미달 제외.
     * runId 를 비우면 (기간이 오면 그 기간의) 최신 성공 산출을 쓴다. 다음 페이지는 meta.nextCursor 로.
     */
    @GetMapping("/demand/candidates")
    public DemandCandidatePage getCandidates(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false) @Min(0) @Max(6) Integer weekday,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime slotStart,
            @RequestParam(defaultValue = "false") boolean candidateOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        DemandQueryService.Query query = new DemandQueryService.Query(
                runId, periodStart, periodEnd, weekday, slotStart, candidateOnly, cursor, limit);
        return demandQueryService.getCandidates(query);
    }

    /** API-44 노선 후보 상세(근거 지표 포함). 로그인 사용자. 없으면 404. */
    @GetMapping("/demand/candidates/{id}")
    public ApiResponse<DemandCandidate> getCandidate(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long id) {
        return ApiResponse.of(demandQueryService.getCandidate(principal.userId(), id));
    }

    /** API-45 내 귀가가 이 노선(거점→목적지) 수요에 반영된 횟수·후보 여부. 학생 본인만. */
    @GetMapping("/me/demand-contribution")
    public ApiResponse<Contribution> getMyContribution(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam @Positive Long runId,
            @RequestParam @Positive Long pickupPointId,
            @RequestParam @Positive Long destinationDongId) {
        return ApiResponse.of(demandContributionService.getMyContribution(
                principal.userId(), runId, pickupPointId, destinationDongId));
    }
}
