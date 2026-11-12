package com.yongyong.owlly.domain.demand.dto;

/**
 * 리포트 상단 집계 카드(귀가·매칭실패·혼자택시·막차 합계).
 *
 * <p>리포트 필터(기간·요일·슬롯)에 걸린 표시 가능한 셀 전체의 합이다. 리포트 생성 시점에 계산해 박제한다.
 */
public record DemandSummary(
        int journeyCount,
        int failedJourneyCount,
        int soloSelectedCount,
        int busSelectedCount) {}
