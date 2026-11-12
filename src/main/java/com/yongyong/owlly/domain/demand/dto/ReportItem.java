package com.yongyong.owlly.domain.demand.dto;

/**
 * 리포트에 담긴 후보 한 줄. candidate 는 출력 당시 박제한 비식별 스냅샷이다(이후 산출이 바뀌어도 고정).
 */
public record ReportItem(
        String estimateId,
        String action,
        int displayOrder,
        DemandCandidate candidate) {}
