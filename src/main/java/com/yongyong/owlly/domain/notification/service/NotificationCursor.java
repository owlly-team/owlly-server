package com.yongyong.owlly.domain.notification.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.notification.entity.Notification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 내 알림 목록의 "여기까지 봤어요" 표시(cursor).
 * 마지막으로 본 알림의 (보낼 시각, id) 를 담고, 앱이 해석하지 않도록 Base64 문자열로 감싼다.
 */
record NotificationCursor(Instant scheduledAt, Long id) {

    static String encode(Notification last) {
        String raw = last.getScheduledAt().toEpochMilli() + ":" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 앱이 보낸 cursor 를 해석한다. 망가진 값이면 422 VALIDATION_ERROR. */
    static NotificationCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new NotificationCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR, List.of("cursor: 형식이 올바르지 않습니다."));
        }
    }
}
