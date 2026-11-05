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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 수동 이체의 양쪽 확인. UNIQUE(settlement_item_id, kind).
 * 은행 입금 확인이 아니라 사용자의 수동 확인.
 */
@Entity
@Getter
@Table(name = "transfers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_item_kind", columnNames = {"settlement_item_id", "kind"})
})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 개인별 정산 FK -> settlement_items.id */
    @Column(name = "settlement_item_id", nullable = false)
    private Long settlementItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferKind kind;

    /** 송금자 FK -> party_members.id */
    @Column(name = "payer_member_id", nullable = false)
    private Long payerMemberId;

    /** 수취자 FK -> party_members.id */
    @Column(name = "payee_member_id", nullable = false)
    private Long payeeMemberId;

    /** 수취 계좌 FK -> room_accounts.id. 계좌 삭제 시 SET NULL. */
    @Column(name = "receiver_account_id")
    private Long receiverAccountId;

    /** 양의 정수 금액(원). */
    @Column(name = "amount_won", nullable = false)
    private int amountWon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "received_at")
    private Instant receivedAt;
}
