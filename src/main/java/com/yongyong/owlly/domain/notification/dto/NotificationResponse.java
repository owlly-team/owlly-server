package com.yongyong.owlly.domain.notification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.yongyong.owlly.domain.notification.entity.Notification;
import com.yongyong.owlly.domain.notification.entity.NotificationType;
import java.time.Instant;

/**
 * 알림 한 건의 응답 (docs/api.md 5절 Notification).
 *
 * <p>알림 목록의 한 줄이 된다. 예) "매칭됐어요 · 23:30 단국대 정문 출발" → 누르면 target 화면으로 이동.
 * title·body·target 은 DB 의 payload(JSON) 안에 저장돼 있던 값을 꺼낸 것이다.
 */
public record NotificationResponse(
        Long id,                // 알림 번호. 읽음 처리할 때 이 번호를 보낸다
        NotificationType type,  // MATCH_RESULT / PREPAY / REFUND / SYSTEM
        Long partyId,           // 관련 팟. 없으면 null
        String title,           // 제목(민감정보 없음)
        String body,            // 내용 요약(민감정보 없음)
        String target,          // 누르면 이동할 앱 화면 경로
        Instant sentAt,         // 푸시 발송 시각. 푸시 발송 기능 전이라 지금은 null
        Instant readAt          // 처음 읽은 시각. 안 읽었으면 null
) {

    public static NotificationResponse of(Notification notification, JsonNode payload) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getPartyId(),
                payload.path("title").asText(""),
                payload.path("body").asText(""),
                payload.path("target").asText(""),
                notification.getSentAt(),
                notification.getReadAt());
    }
}
