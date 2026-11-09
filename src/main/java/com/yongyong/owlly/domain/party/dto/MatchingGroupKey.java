package com.yongyong.owlly.domain.party.dto;

import java.time.Instant;

/**
 * 같은 팟이 될 수 있는 신청 묶음의 조건(매칭 키, ERD 6절).
 * 학교·출발 거점·출발 시각(30분 슬롯)·도착 권역(구)이 모두 같아야 한다.
 */
public record MatchingGroupKey(
        Long universityId,    // 학교
        Long pickupPointId,   // 출발 거점
        Long districtId,      // 도착 권역(구 또는 시)
        Instant departureAt   // 출발 시각(날짜 포함 30분 슬롯)
) {}
