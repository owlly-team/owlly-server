package com.yongyong.owlly.domain.safety.dto;

import com.yongyong.owlly.domain.safety.entity.SafetyReport;
import com.yongyong.owlly.domain.safety.entity.SafetyReportStatus;
import java.time.Instant;

/** 신고 당사자·사유·증거 원문은 응답에 노출하지 않는다. */
public record SafetyReportResponse(Long id, SafetyReportStatus status, Instant evidenceDeleteAfter, boolean rematchingBlocked) {
    public static SafetyReportResponse of(SafetyReport report) {
        return new SafetyReportResponse(report.getId(), report.getStatus(), report.getEvidenceDeleteAfter(), true);
    }
}
