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
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신고 사건과 제한된 증거. 동일 팟의 본인 외 다른 멤버만 신고. */
@Entity
@Getter
@Table(name = "safety_reports", uniqueConstraints = @UniqueConstraint(name = "uk_safety_report_room_reporter_target",
        columnNames = {"room_id", "reporter_member_id", "target_member_id"}),
        indexes = @Index(name = "idx_safety_report_evidence_expiry", columnList = "evidence_delete_after"))
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

    /** 신고 시점까지 방 전체 대화와 선택 설명을 마스킹·암호화한 사본. 만료 시 NULL. */
    @Column(name = "evidence_encrypted", columnDefinition = "text")
    private String evidenceEncrypted;

    /** 기획 질문 v3: 신고 접수 시각부터 7일. 재시도·다른 신고로 연장하지 않는다. */
    @Column(name = "evidence_delete_after", nullable = false)
    private Instant evidenceDeleteAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SafetyReportStatus status;
}
