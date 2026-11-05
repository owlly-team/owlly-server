package com.yongyong.owlly.domain.demand.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 리포트에 선택한 후보와 조치. UNIQUE(report_id, estimate_id).
 * 서로 다른 산출 실행의 후보를 한 리포트에 혼합하지 않는다.
 */
@Entity
@Getter
@Table(name = "stakeholder_report_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_item", columnNames = {"report_id", "estimate_id"})
})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StakeholderReportItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> stakeholder_reports.id */
    @Column(name = "report_id", nullable = false)
    private Long reportId;

    /** 선택한 노선 후보 FK -> demand_estimates.id */
    @Column(name = "estimate_id", nullable = false)
    private Long estimateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportAction action;

    /** 리포트 내 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 출력 당시 구간·순위·근거·단위의 비식별 스냅샷. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_snapshot", nullable = false, columnDefinition = "jsonb")
    private String candidateSnapshot;
}
