package com.yongyong.owlly.domain.transit.dto;

import java.time.Instant;
import java.util.List;

/**
 * 막차로 가는 방법 (docs/api.md 5절 TransitOption). 막차가 끊겼거나 안내할 수 없는 구간이면 available 이 false 다.
 * 예) 22:49 까지 출발해야 하고 1,550원, 43분 걸려 23:41 에 영통2동 도착.
 */
public record TransitOption(
        boolean available,          // 안내할 수 있는 막차가 있는지. false 면 아래 값은 비어 있다
        Instant lastLeaveAt,        // 이 시각까지 출발해야 막차를 탄다(현재 위치 기준, 위치가 없으면 거점 기준)
        Instant arrivalAt,          // 예상 도착 시각
        Integer estimatedCostWon,   // 1인 예상 교통비(교통카드 기준)
        Integer totalDurationMin,   // 버스를 탄 뒤 도착까지 걸리는 분(화면의 큰 글씨 "43분")
        List<TransitLeg> legs,      // 걷기 → 버스 → 걷기 단계 목록
        String sourceLabel,         // 값의 출처와 추정 여부 안내. 예) "경기도 버스 시간표(GBIS) 기준"
        Instant asOf                // 이 값을 계산한 시각
) {}
