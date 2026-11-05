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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인별 예상·최종 부담과 환급. UNIQUE(member_id): 한 팟 참여자는 한 정산 행만.
 * 대표 자기 선송금·환급은 0, 자기 이체 행 없음. 음수 환급은 REVIEW_REQUIRED.
 */
@Entity
@Getter
@Table(name = "settlement_items")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> settlements.id */
    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    /** 부담 멤버 FK -> party_members.id. UNIQUE. */
    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    /** 예상 거리비례 부담액(원). */
    @Column(name = "estimated_share_won", nullable = false)
    private int estimatedShareWon;

    /** 대표 제외 ceil(예상 부담액 ×1.2 /100)×100. */
    @Column(name = "prepay_due_won", nullable = false)
    private int prepayDueWon;

    /** 실제 요금 기준 부담액(원). 확정 전 NULL. */
    @Column(name = "actual_share_won")
    private Integer actualShareWon;

    /** 환급액(원). 일반 멤버: 수령 선송금 − 최종 부담액, 대표: 0. 확정 전 NULL. */
    @Column(name = "refund_due_won")
    private Integer refundDueWon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementItemStatus status;
}
