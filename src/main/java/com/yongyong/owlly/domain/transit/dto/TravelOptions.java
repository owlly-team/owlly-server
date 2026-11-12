package com.yongyong.owlly.domain.transit.dto;

import com.yongyong.owlly.domain.journey.dto.MatchingSlot;
import java.time.Instant;
import java.util.List;

/**
 * 막차·동승·혼자 택시 비교 결과 (docs/api.md 5절 TravelOptions).
 * 홈의 세 카드(막차, 동승, 혼자 택시)와 막차 경로·혼자 택시 화면이 쓴다. 모든 값은 추정이다.
 * ID 는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 */
public record TravelOptions(
        TransitOption transit,             // 막차
        Integer soloTaxiEstimatedWon,      // 혼자 택시 지금 출발 예상 요금. 모르면 null
        Integer soloTaxiDistanceM,         // 혼자 택시 거리(m). 모르면 null
        Integer soloTaxiDurationMin,       // 혼자 택시 걸리는 분. 모르면 null
        Instant estimatedArrivalAt,        // 혼자 택시로 지금 출발하면 도착하는 시각. 모르면 null
        Integer poolEstimatedShareWon,     // 동승하면 1인 예상 분담액. 같은 조건 대기 인원이 없으면 null
        MatchingSlot nearestPoolSlot,      // 가장 가까운 동승 시간대와 그 대기 인원. 등록할 수 있는 시간대가 없으면 null
        Long destinationDongId,            // 하차 핀으로 판별한 행정동
        String destinationDongName,        // 하차 동 이름. 예) "영통3동"
        Long districtId,                   // 같은 방향 매칭 권역(구, 구가 없는 시는 시)
        String districtName,               // 권역 이름. 예) "영통구"
        Instant asOf,                      // 추정 기준 시각
        List<String> warnings              // 불확실하거나 비어 있는 값에 대한 안내
) {}
