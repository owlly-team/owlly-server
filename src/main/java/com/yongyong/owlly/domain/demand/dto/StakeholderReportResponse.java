package com.yongyong.owlly.domain.demand.dto;

import com.yongyong.owlly.domain.demand.entity.StakeholderReport;
import java.time.Instant;
import java.util.List;

/**
 * 리포트 미리보기 응답(API-46·47·48).
 *
 * <p>filters·items 는 생성 당시 박제한 스냅샷에서 복원한다. downloadAvailable 은 파일이 생성돼 받을 수 있는지로,
 * 파일 생성(API-49·50)은 범위 밖이라 현재는 항상 false.
 */
public record StakeholderReportResponse(
        String id,
        String runId,
        String format,
        String status,
        ReportFilters filters,
        DemandSummary summary,
        List<ReportItem> items,
        String narrative,
        Instant generatedAt,
        boolean downloadAvailable) {

    public static StakeholderReportResponse of(
            StakeholderReport report, ReportFilters filters, DemandSummary summary, List<ReportItem> items) {
        return new StakeholderReportResponse(
                String.valueOf(report.getId()),
                String.valueOf(report.getRunId()),
                report.getFormat().name(),
                report.getStatus().name(),
                filters,
                summary,
                items,
                report.getNarrative(),
                report.getGeneratedAt(),
                report.getFileObjectKey() != null);
    }
}
