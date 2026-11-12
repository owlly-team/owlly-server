package com.yongyong.owlly.domain.settlement.entity;

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

/**
 * 팟의 예상금액과 실제 정산. 팟당 정산 1개(UNIQUE).
 * 실제 요금 상한은 예상 전체 요금 ×1.2.
 */
@Entity
@Getter
@Table(name = "settlements")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> ride_parties.id. UNIQUE(팟당 1개). */
    @Column(name = "party_id", nullable = false, unique = true)
    private Long partyId;

    /** 전체 예상 택시요금(원). */
    @Column(name = "estimated_total_won", nullable = false)
    private int estimatedTotalWon;

    /** 대표가 입력한 실제 요금(원). 확정 전 NULL. */
    @Column(name = "actual_total_won")
    private Integer actualTotalWon;

    /** 분담 계산에 사용하는 하차 경로 버전. */
    @Column(name = "route_version", nullable = false)
    private int routeVersion;

    /** 비공개 영수증 파일 키. 금액만 입력 시 NULL. */
    @Column(name = "receipt_object_key")
    private String receiptObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /** 환급 기한. 7일의 기산점 확인 필요. */
    @Column(name = "refund_due_at")
    private Instant refundDueAt;

    /**
     * 멤버 이탈로 경로가 바뀌어 예상 정산을 다시 계산할 때 쓴다(API-24).
     * 새 경로 버전과 새 예상 전체 요금을 반영한다. 확정된 정산은 서비스에서 먼저 막는다.
     */
    public void reEstimate(int routeVersion, int estimatedTotalWon) {
        this.routeVersion = routeVersion;
        this.estimatedTotalWon = estimatedTotalWon;
    }
}
