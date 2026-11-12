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
import jakarta.persistence.Index;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마스킹된 대화와 시스템 안내. 마스킹 전 원문은 로그·푸시에도 남기지 않는다. */
@Entity
@Getter
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room_id_id", columnList = "room_id,id"))
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> chat_rooms.id */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 발신자 FK -> party_members.id. 시스템 메시지는 NULL. */
    @Column(name = "sender_member_id")
    private Long senderMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    /** 전화번호·링크·계좌 문자열을 마스킹한 저장 본문. */
    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 메시지 재전송 중복 방지. */
    @Column(name = "client_message_id", nullable = false, unique = true)
    private UUID clientMessageId;

    /** 원문 대신 HMAC으로 같은 clientMessageId의 요청을 비교한다. SYSTEM은 NULL. */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    /** 클라이언트 UUID와 분리한 시스템 이벤트 중복 방지 키. */
    @Column(name = "system_event_key", unique = true, length = 120)
    private String systemEventKey;
}
