package com.yongyong.owlly.domain.journey.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.journey.dto.MatchingSlot;
import com.yongyong.owlly.domain.journey.service.MatchingSlotService;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동승 출발 시간대 API. 로그인한 학생의 학교·성별·차단 관계로 대기 인원을 센다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchingSlotController {

    private final MatchingSlotService matchingSlotService;

    /**
     * API-18 조건별 출발 슬롯·대기 수 조회. 출발 시간 선택 화면이 시간 칩마다 "지금 대기 인원"을 보여 줄 때.
     * 예) GET /matching-slots?pickupPointId=11&destinationDongId=105&date=2026-11-05&sameGenderOnly=true
     */
    @GetMapping("/matching-slots")
    public ApiResponse<List<MatchingSlot>> getMatchingSlots(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam @Positive Long pickupPointId,
            @RequestParam @Positive Long destinationDongId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam boolean sameGenderOnly) {
        return ApiResponse.of(matchingSlotService.getSlots(
                principal.userId(), pickupPointId, destinationDongId, date, sameGenderOnly, Instant.now()));
    }
}
