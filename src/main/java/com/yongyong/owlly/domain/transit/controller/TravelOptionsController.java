package com.yongyong.owlly.domain.transit.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.transit.dto.TravelOptions;
import com.yongyong.owlly.domain.transit.dto.TravelOptionsRequest;
import com.yongyong.owlly.domain.transit.service.TravelOptionsService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 귀가 방법 비교 API. 홈이 막차·동승·혼자 택시를 한 번에 비교할 때 쓴다.
 * 하차 핀과 현재 위치는 이 계산에만 쓰고 저장하지 않는다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TravelOptionsController {

    private final TravelOptionsService travelOptionsService;

    /**
     * API-14 막차·동승·혼자 택시 비교. 읽기만 하는 계산이라 Idempotency-Key 가 없다(POST 인 이유는 위치를 본문으로 받기 위해서).
     */
    @PostMapping("/travel-options")
    public ApiResponse<TravelOptions> compareTravelOptions(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody TravelOptionsRequest request) {
        return ApiResponse.of(travelOptionsService.compare(principal.userId(), request, Instant.now()));
    }
}
