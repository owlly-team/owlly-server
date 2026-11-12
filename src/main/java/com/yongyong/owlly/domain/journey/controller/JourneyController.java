package com.yongyong.owlly.domain.journey.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.journey.dto.JourneyChoice;
import com.yongyong.owlly.domain.journey.dto.JourneyCreate;
import com.yongyong.owlly.domain.journey.dto.JourneyPage;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.dto.RideRequestCreate;
import com.yongyong.owlly.domain.journey.dto.RideRequestResponse;
import com.yongyong.owlly.domain.journey.service.JourneyCreateService;
import com.yongyong.owlly.domain.journey.service.JourneyService;
import com.yongyong.owlly.domain.journey.service.RideRequestCreateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 귀가 (journey) API.
 *
 * <p>로그인한 학생 본인 것만 다룬다. 학생 id 는 세션에서 꺼내고, 요청으로 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;
    private final JourneyCreateService journeyCreateService;
    private final RideRequestCreateService rideRequestCreateService;

    /**
     * API-16 내 귀가 기록 조회. 최근 귀가부터 limit 개씩, 다음 페이지는 meta.nextCursor 로.
     * 예) 홈에서 진행 중인 동승 카드를 붙일지, 내 정보에서 지난 귀가를 볼 때.
     */
    @GetMapping("/me/journeys")
    public ApiResponse<List<JourneyResponse>> getMyJourneys(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        JourneyPage page = journeyService.getMyJourneys(principal.userId(), cursor, limit);
        return ApiResponse.of(page.journeys(), new ApiResponse.PageMeta(page.nextCursor(), page.hasMore()));
    }

    /**
     * API-15 귀가 생성. 거점과 하차 핀으로 귀가 한 건을 만든다(결과는 UNRESOLVED로 시작).
     * 같은 clientJourneyKey로 다시 보내면 먼저 만든 귀가를 돌려주므로, 응답을 못 받았을 때 그대로 재시도해도 안전하다.
     */
    @PostMapping("/journeys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JourneyResponse> createJourney(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody JourneyCreate request) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return ApiResponse.of(journeyCreateService.create(principal.userId(), request));
    }

    /**
     * API-19 동승 신청·실패 후 재등록. 귀가에 동승 신청을 붙인다(WAITING으로 시작).
     * 매칭에 실패한 뒤 "다음 시간대로 다시 등록"할 때도 같은 귀가에 이 API를 새 clientRequestKey로 다시 호출한다.
     */
    @PostMapping("/journeys/{journeyId}/ride-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RideRequestResponse> createRideRequest(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long journeyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RideRequestCreate request) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return ApiResponse.of(rideRequestCreateService.create(principal.userId(), journeyId, request, Instant.now()));
    }

    /**
     * API-17 막차 또는 혼자 택시 선택 기록. 매칭 대기 중이면 신청을 같이 취소한다.
     * 같은 선택을 같은 키로 다시 보내면 현재 결과를 돌려주므로 안전하다.
     */
    @PutMapping("/journeys/{journeyId}/choice")
    public ApiResponse<JourneyResponse> chooseOutcome(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long journeyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody JourneyChoice request) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return ApiResponse.of(journeyService.chooseOutcome(principal.userId(), journeyId, request.mode(), Instant.now()));
    }
}
