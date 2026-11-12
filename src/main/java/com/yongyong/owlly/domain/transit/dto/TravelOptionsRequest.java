package com.yongyong.owlly.domain.transit.dto;

import com.yongyong.owlly.domain.journey.dto.Point;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

/**
 * 막차·동승·혼자 택시 비교 요청 (docs/api.md 5절 TravelOptionsRequest).
 * 예) 단국대 정문 거점에서 영통으로 23:30 에 가려는 학생이 홈에서 세 가지를 비교
 *     {"pickupPointId": "11", "dropoff": {"latitude": 37.25, "longitude": 127.07},
 *      "departureAt": "2026-11-05T23:30:00+09:00", "currentLocation": {"latitude": 37.331, "longitude": 127.271}}
 */
public record TravelOptionsRequest(
        @NotNull @Positive Long pickupPointId,  // 선택한 출발 거점
        @NotNull @Valid Point dropoff,          // 하차 핀. 저장하지 않고 이 계산에만 쓴다
        @NotNull Instant departureAt,           // 비교 기준 시각(막차 운행일과 동승 시간대를 정하는 기준)
        @Valid Point currentLocation            // 지금 위치(선택). 도보 시간 계산에만 쓰고 어디에도 저장하지 않는다
) {}
