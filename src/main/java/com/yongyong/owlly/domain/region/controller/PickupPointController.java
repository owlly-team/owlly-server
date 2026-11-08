package com.yongyong.owlly.domain.region.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.domain.region.dto.PickupPointResponse;
import com.yongyong.owlly.domain.region.service.PickupPointService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-04 고정 출발 거점 목록 (GET /api/v1/pickup-points).
 *
 * <p>학생 앱이 "어디서 출발할까요?" 화면을 열 때 호출한다.
 * 권한은 학생(STUDENT)이며, 로그인·권한 확인은 인증 기능이 들어온 뒤 붙인다.
 */
@RestController
@RequestMapping("/api/v1/pickup-points")
@RequiredArgsConstructor
public class PickupPointController {

    private final PickupPointService pickupPointService;

    /**
     * @param universityId 학교 거점 필터(선택). 주면 그 학교 전용 + 공용 거점, 안 주면 활성 거점 전체.
     */
    @GetMapping
    public ApiResponse<List<PickupPointResponse>> getPickupPoints(
            @RequestParam(required = false) @Positive Long universityId) {
        return ApiResponse.of(pickupPointService.getPickupPoints(universityId));
    }
}
