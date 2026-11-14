package com.yongyong.owlly.domain.demand.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 노선 구간별 관측·추정 수요.
 * UNIQUE(run_id, university_id, pickup_point_id, destination_dong_id, weekday, slot_start).
 * 실패 수와 최종 수단 수를 더해 총수요로 쓰지 않는다.
 */
@Entity
@Getter
@Table(name = "demand_estimates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_demand_estimate_cell", columnNames = {
                "run_id", "university_id", "pickup_point_id", "destination_dong_id", "weekday", "slot_start"
        })
})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemandEstimate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 산출 실행 FK -> demand_runs.id */
    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** 대학 단위 FK -> universities.id */
    @Column(name = "university_id", nullable = false)
    private Long universityId;

    /** 출발 거점 FK -> pickup_points.id */
    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    /** 목적지 동 FK -> administrative_areas.id */
    @Column(name = "destination_dong_id", nullable = false)
    private Long destinationDongId;

    /** 서비스 현지 요일 0~6 (0=일요일). */
    @Column(nullable = false)
    private int weekday;

    /** 현지 30분 슬롯 시작. */
    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    /** 중복 제거한 귀가 건수. */
    @Column(name = "journey_count", nullable = false)
    private int journeyCount;

    @Column(name = "failed_journey_count", nullable = false)
    private int failedJourneyCount;

    @Column(name = "pooled_journey_count", nullable = false)
    private int pooledJourneyCount;

    /**
     * 동승 등록 건수(동승을 신청한 수). 매칭 실패율의 분모다(실패율 = failed_journey_count / pooled_registered_count).
     * AI 산출이 함께 채운다. 과거 산출엔 없을 수 있어 NULL 허용.
     */
    @Column(name = "pooled_registered_count")
    private Integer pooledRegisteredCount;

    @Column(name = "bus_selected_count", nullable = false)
    private int busSelectedCount;

    @Column(name = "solo_selected_count", nullable = false)
    private int soloSelectedCount;

    /** 주간 추정치. 미산출이면 NULL. */
    @Column(name = "estimated_weekly_demand", precision = 12, scale = 4)
    private BigDecimal estimatedWeeklyDemand;

    /** 후보가 되기 위한 최소 수요. 산정 정책 미확정. */
    @Column(name = "candidate_threshold", precision = 12, scale = 4)
    private BigDecimal candidateThreshold;

    /** 추정 수요 순 후보 순위. 후보 아니면 NULL. */
    @Column(name = "candidate_rank")
    private Integer candidateRank;

    /**
     * 기존 공급(이미 다니는 심야 노선). 예: "N1 하루 1회", "1101N 평일 2회", "없음".
     * 고정 입력이라 AI 산출이 함께 채우고, 리포트 표의 "기존 공급" 열에 그대로 쓴다. 미설정이면 NULL.
     */
    @Column(name = "existing_supply", columnDefinition = "text")
    private String existingSupply;

    /** 공급 격차·근거와 출처/관측시각/단위. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String evidence;

    /** 최소 표본 미달로 표시 제한 여부(5건 미만 숨김). */
    @Column(name = "is_suppressed", nullable = false)
    private boolean suppressed;
}
