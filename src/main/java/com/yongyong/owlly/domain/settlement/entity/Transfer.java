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

    /**
     * 송금자가 '보냈어요'를 누른 것을 기록한다(PENDING → SENT).
     * 예) 선송금을 보낸 뒤 송금자가 확인 버튼을 누를 때. 보낼 수 있는 상태·수취계좌 여부는 서비스에서 먼저 확인한다.
     * 실제 은행 이체를 실행하지 않고, 사용자의 수동 확인만 기록한다.
     */
    public void markSent(Instant sentAt) {
        this.status = TransferStatus.SENT;
        this.sentAt = sentAt;
    }

    /**
     * 수취자가 '받았어요'를 누른 것을 기록한다(PENDING/SENT → RECEIVED).
     * 예) 환급을 받은 멤버가 확인 버튼을 누를 때. 송금자의 '보냈어요'보다 먼저 와도 허용한다(실제 수령이 먼저일 수 있음).
     */
    public void markReceived(Instant receivedAt) {
        this.status = TransferStatus.RECEIVED;
        this.receivedAt = receivedAt;
    }

    /**
     * 멤버 이탈로 경로가 바뀌어 아직 안 보낸(PENDING) 선송금 금액을 다시 맞출 때 쓴다(API-24).
     * 이미 보냈거나 받은 송금은 서비스에서 먼저 걸러 바꾸지 않는다.
     */
    public void changeAmount(int amountWon) {
        this.amountWon = amountWon;
    }

    /** 멤버 이탈로 더는 필요 없어진 PENDING 선송금을 취소한다(API-24). */
    public void cancel() {
        this.status = TransferStatus.CANCELLED;
    }

    public boolean isPending() {
        return this.status == TransferStatus.PENDING;
    }
}
