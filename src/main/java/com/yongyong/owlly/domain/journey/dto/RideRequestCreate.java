package com.yongyong.owlly.domain.journey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

/**
 * 동승 신청 요청 (docs/api.md 5절 RideRequestCreate).
 * 예) 23:30 에 단국대 정문에서 출발해 영통으로, 동성끼리만 타겠다는 신청.
 *     {"clientRequestKey": "0b18e222-…", "pickupPointId": "11", "dropoff": {"latitude": 37.33, "longitude": 127.27},
 *      "departureAt": "2026-11-05T23:30:00+09:00", "sameGenderOnly": true}
 * 같은 귀가에서 매칭에 실패해 다시 등록할 때도 같은 모양이고 clientRequestKey 만 새로 만든다.
 */
public record RideRequestCreate(
        @NotNull UUID clientRequestKey,        // 신청 생성 재시도용 멱등키. 재등록할 때는 새 값을 쓴다
        @NotNull @Positive Long pickupPointId, // 이번 시도의 출발 거점
        @NotNull @Valid Point dropoff,         // 하차 핀. 행정동·권역을 판별하는 데 쓰고, 좌표는 암호화해 저장한다
        @NotNull Instant departureAt,          // 날짜를 포함한 30분 단위 출발 시각
        @NotNull Boolean sameGenderOnly        // 이번 신청에서 동성끼리만 탈지
) {}
