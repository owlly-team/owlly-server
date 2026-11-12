package com.yongyong.owlly.domain.chat.dto;

import com.yongyong.owlly.domain.chat.entity.RoomStatus;
import java.time.Instant;

public record RoomResponse(Long id, Long partyId, RoomStatus status, String meetingNote, Instant departureAt,
        String verificationCode, Instant closedAt, Instant deleteAfter, boolean canSendMessage) {}
