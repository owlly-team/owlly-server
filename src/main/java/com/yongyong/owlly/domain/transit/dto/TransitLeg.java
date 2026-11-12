package com.yongyong.owlly.domain.transit.dto;

import java.time.Instant;

/**
 * 막차 경로의 한 단계 (docs/api.md 5절 TransitLeg).
 * 예) 걷기 22:49 → 22:53 "단국대 정문 거점 → 단국대정문 정류장", 버스 22:58 → 23:38 "720-3", 걷기 23:38 → 23:50.
 */
public record TransitLeg(
        String mode,           // 이동 수단: WALK / BUS / SUBWAY
        String routeName,      // 노선 이름. 걷기면 null
        String fromName,       // 출발 지점 이름
        String toName,         // 도착 지점 이름
        Instant departureAt,   // 이 단계를 시작하는 시각
        Instant arrivalAt,     // 이 단계가 끝나는 시각
        Integer distanceM,     // 걷는 거리(m). 버스 구간이면 null
        Integer stopCount,     // 버스로 가는 정류장 수. 걷기면 null
        boolean lastBus        // 오늘 이 방향 마지막 버스인지
) {}
