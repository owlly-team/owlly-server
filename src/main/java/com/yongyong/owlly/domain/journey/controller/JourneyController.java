package com.yongyong.owlly.domain.journey.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.journey.dto.JourneyPage;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.service.JourneyService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
