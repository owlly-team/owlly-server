package com.yongyong.owlly.domain.journey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

/**
 * 귀가 생성 요청 (docs/api.md 5절 JourneyCreate).
 * 예) 단국대 정문에서 출발해 영통으로 23:30에 가려는 학생
 *     {"clientJourneyKey": "0b18e222-…", "pickupPointId": "11", "dropoff": {"latitude": 37.33, "longitude": 127.27},
 *      "intendedDepartureAt": "2026-11-05T23:30:00+09:00"}
 * ID 는 JSON 에서 문자열로 오지만 Long 으로 받는다.
 */
public record JourneyCreate(
        @NotNull UUID clientJourneyKey,       // 귀가 생성 재시도용 멱등키. 같은 귀가를 다시 보낼 때 같은 값을 쓴다
        @NotNull @Positive Long pickupPointId, // 최종 출발 거점
        @NotNull @Valid Point dropoff,        // 하차 핀. 행정동만 저장하고 좌표는 저장하지 않는다
        @NotNull Instant intendedDepartureAt  // 귀가 예정 시각
) {}
