package com.yongyong.owlly.domain.fare.dto;

import java.util.List;

/**
 * 출발 거점에서 하차 지점들을 순서대로 지나는 택시 경로의 예상 거리·요금.
 * 예) 단국대 정문 → 김민준 → 한서윤 → 박지우: 구간 거리 [8000, 4000, 4000], 전체 16000m, 예상 요금 30,000원.
 */
public record RouteEstimate(
        List<Integer> segmentDistancesM,  // 구간별 거리(m). 하차 순서와 같은 순서. 첫 구간은 거점 → 첫 하차
        int totalDistanceM,               // 전체 거리(m)
        int totalFareWon,                 // 전체 예상 택시 요금(원). 통행료 포함
        String routingSource              // 거리·요금을 어떻게 계산했는지. party_stops.routing_source 에 저장
) {}
