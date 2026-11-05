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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 수요 산출 실행과 입력 근거. 앱 관측과 공공데이터 추정을 구분. */
@Entity
@Getter
@Table(name = "demand_runs")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemandRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 추정/집계 방식 버전. 모델 미정. */
    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    /** 출처·조회시각·단위·공간/시간 해상도·스냅샷 식별자. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_sources", nullable = false, columnDefinition = "jsonb")
    private String inputSources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandRunStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;
}
