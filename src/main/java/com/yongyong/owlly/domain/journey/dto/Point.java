package com.yongyong.owlly.domain.journey.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 지도에서 찍은 위치 (docs/api.md 5절 Point).
 * 예) 하차 핀 {"latitude": 37.33, "longitude": 127.27}
 */
public record Point(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,     // 위도
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude   // 경도
) {}
