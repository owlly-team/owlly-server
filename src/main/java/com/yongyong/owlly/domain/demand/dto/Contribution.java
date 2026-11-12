package com.yongyong.owlly.domain.demand.dto;

import java.math.BigDecimal;

/**
 * 내 귀가가 특정 노선(거점→목적지) 수요에 얼마나 반영됐는지(API-45).
 *
 * <p>myJourneyCount 는 해당 산출 기간 동안 내가 그 거점·목적지로 남긴 귀가 수다.
 * visibility 는 셀 표본에 따라 노출 수준을 나눈다(최소 표본 미달이면 수치를 가린다).
 * remainingToCandidate(후보까지 남은 수요)는 산정 정책이 확정되기 전까지 null 이다.
 */
public record Contribution(
        String runId,
        long myJourneyCount,
        Visibility visibility,
        String candidateId,
        BigDecimal remainingToCandidate) {

    /** 수요 셀 노출 수준. */
    public enum Visibility {
        /** 표본 충분, 정상 노출. */
        VISIBLE,
        /** 최소 표본 미달(5건 미만)로 수치 제한. */
        INSUFFICIENT_SAMPLE,
        /** 해당 거점·목적지의 수요 셀이 없음. */
        NOT_AVAILABLE
    }
}
