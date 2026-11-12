package com.yongyong.owlly.domain.chat.dto;

import com.yongyong.owlly.domain.chat.entity.*;
import java.time.Instant;

public record MessageResponse(Long id, Long roomId, Long senderMemberId, MessageType messageType, String body, Instant createdAt) {
    public static MessageResponse of(ChatMessage m) {
        return new MessageResponse(m.getId(), m.getRoomId(), m.getSenderMemberId(), m.getMessageType(), m.getBody(), m.getCreatedAt());
    }
}
