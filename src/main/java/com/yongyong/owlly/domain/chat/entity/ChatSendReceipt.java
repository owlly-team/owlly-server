package com.yongyong.owlly.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메시지가 보존되는 동안 유지하는 전송 멱등 기록. 입력 원문은 저장하지 않는다. */
@Entity
@Table(name = "chat_send_receipts")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSendReceipt {
    @Id @Column(name = "request_key", length = 36) private String key;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "message_id", nullable = false) private Long messageId;
}
