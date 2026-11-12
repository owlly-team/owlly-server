package com.yongyong.owlly.domain.chat.entity;

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

/** 팟 전용 채팅의 생명주기. 팟당 최대 1개. */
@Entity
@Getter
@Table(name = "chat_rooms")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> ride_parties.id. UNIQUE(팟당 1개). */
    @Column(name = "party_id", nullable = false, unique = true)
    private Long partyId;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** 일반 채팅 삭제 예정. 정책 확정 후 계산. */
    @Column(name = "delete_after")
    private Instant deleteAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    public void close(Instant when) {
        if (status == RoomStatus.CLOSED) return;
        status = RoomStatus.CLOSED;
        closedAt = when;
        // 신고 증거 보존 정책이 연결되기 전에는 임의의 물리 삭제 시각을 정하지 않는다.
    }
}
