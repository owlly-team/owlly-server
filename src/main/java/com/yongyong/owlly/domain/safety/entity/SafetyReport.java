package com.yongyong.owlly.domain.safety.entity;

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

/** 신고 사건과 제한된 증거. 동일 팟의 본인 외 다른 멤버만 신고. */
@Entity
@Getter
@Table(name = "safety_reports")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신고 발생 방 FK -> chat_rooms.id */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 신고자 FK -> party_members.id */
    @Column(name = "reporter_member_id", nullable = false)
    private Long reporterMemberId;

    /** 신고 대상 FK -> party_members.id */
    @Column(name = "target_member_id", nullable = false)
    private Long targetMemberId;

    /** 특정 메시지 신고 시 FK -> chat_messages.id. 메시지 삭제 시 SET NULL. */
    @Column(name = "message_id")
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private ReportReasonCode reasonCode;

    /** 마스킹된 대화 증거의 암호화 스냅샷. */
    @Column(name = "evidence_encrypted", columnDefinition = "text")
    private String evidenceEncrypted;

    /** 신고 기록 7일 보존 후 증거 삭제 예정. */
    @Column(name = "evidence_delete_after", nullable = false)
    private Instant evidenceDeleteAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SafetyReportStatus status;
}
