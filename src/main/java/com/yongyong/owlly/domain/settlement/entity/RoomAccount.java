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
 * 채팅방에서만 노출하는 수취 계좌. 방 종료 시 삭제 예정.
 * 장기 계좌 테이블은 만들지 않는다.
 */
@Entity
@Getter
@Table(name = "room_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_account", columnNames = {"room_id", "owner_member_id", "purpose"})
})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 유효 범위인 채팅방 FK -> chat_rooms.id */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 계좌 소유 멤버 FK -> party_members.id */
    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountPurpose purpose;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "account_number_encrypted", nullable = false, columnDefinition = "text")
    private String accountNumberEncrypted;

    @Column(name = "holder_name_encrypted", nullable = false, columnDefinition = "text")
    private String holderNameEncrypted;

    /** 방 종료 시 삭제 예정. 닫힘 전 NULL. */
    @Column(name = "delete_after")
    private Instant deleteAfter;

    /**
     * 이미 등록된 계좌를 새 값으로 교체한다(같은 방·멤버·용도의 재등록).
     * 암호화는 서비스에서 끝낸 값을 받는다(엔티티는 암호화를 모른다).
     */
    public void changeAccount(String bankCode, String accountNumberEncrypted, String holderNameEncrypted) {
        this.bankCode = bankCode;
        this.accountNumberEncrypted = accountNumberEncrypted;
        this.holderNameEncrypted = holderNameEncrypted;
    }
}
