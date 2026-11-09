package com.yongyong.owlly.domain.notification.entity;

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

/**
 * 매칭·정산 알림 이력과 발송 예약.
 * 푸시에는 실명·정확한 하차 핀·계좌를 포함하지 않는다.
 */
@Entity
@Getter
@Table(name = "notifications")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수신 계정 FK -> users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 관련 팟 FK -> ride_parties.id. 일반 알림은 NULL. */
    @Column(name = "party_id")
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    /** 동일 사건 알림 중복 방지. */
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 200)
    private String dedupeKey;

    /** 이동할 화면과 최소 요약. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    /**
     * 처음 읽은 시각을 남긴다. 이미 읽었으면 그대로 둔다(여러 번 눌러도 최초 시각 유지).
     * 예) 학생이 알림 목록에서 "매칭됐어요" 알림을 누를 때.
     */
    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
