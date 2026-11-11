package com.yongyong.owlly.domain.journey.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.journey.dto.RideRequestResponse;
import com.yongyong.owlly.domain.journey.service.RideRequestService;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동승 신청 (ride request) API. 본인 신청만 다루고, 남의 신청은 404 로 숨긴다(서비스에서 확인).
 */
@RestController
@RequestMapping("/api/v1/ride-requests")
@RequiredArgsConstructor
public class RideRequestController {

    private final RideRequestService rideRequestService;

    /**
     * API-20 신청·매칭 결과 조회. 매칭 대기 카드가 "매칭됐나?"를 확인할 때. 응답에 ETag 를 붙인다.
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<RideRequestResponse>> getRideRequest(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long requestId) {
        RideRequestResponse response = rideRequestService.getMyRideRequest(principal.userId(), requestId);
        return ResponseEntity.ok().eTag(ETags.of(response)).body(ApiResponse.of(response));
    }

    /**
     * API-21 대기 신청 취소. 매칭 대기 중 "등록 취소"(출발 15분 전까지). 이미 취소했으면 같은 응답이라 같은 키로 다시 보내도 안전하다.
     */
    @PostMapping("/{requestId}/cancel")
    public ApiResponse<RideRequestResponse> cancelRideRequest(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return ApiResponse.of(rideRequestService.cancelMyRideRequest(principal.userId(), requestId, Instant.now()));
    }
}
