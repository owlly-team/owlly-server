package com.yongyong.owlly.domain.demand.dto;

/**
 * 수요 지도 상단 집계 카드(화면 S-수요지도).
 *
 * <p>지금 필터(기간·요일·슬롯)에 걸린 표시 가능한 셀 전체의 합이다. 목록은 후보/페이지 단위라 합산 대상이 다르므로,
 * 서버가 run·필터 기준으로 한 번에 합쳐 내려 준다(표본 미달 셀 제외).
 */
public record DemandSummary(
        long journeyCount,
        long failedJourneyCount,
        long soloSelectedCount,
        long busSelectedCount) {}
