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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 학교 관계자의 리포트 생성 이력. 원시 사용자·핀·채팅·계좌를 제공하지 않는다. */
@Entity
@Getter
@Table(name = "stakeholder_reports")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StakeholderReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** STAFF 계정 FK -> users.id */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** 사용한 산출 버전 FK -> demand_runs.id */
    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** 기간·학교·요일·시간대 등 선택 조건. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters_snapshot", nullable = false, columnDefinition = "jsonb")
    private String filtersSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StakeholderReportStatus status;

    /** 생성된 비공개 파일 키. */
    @Column(name = "file_object_key")
    private String fileObjectKey;

    @Column(name = "generated_at")
    private Instant generatedAt;
}
