package com.yongyong.owlly.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.notification.dto.NotificationMessage;
import com.yongyong.owlly.domain.notification.dto.NotificationPage;
import com.yongyong.owlly.domain.notification.dto.NotificationResponse;
import com.yongyong.owlly.domain.notification.entity.Notification;
import com.yongyong.owlly.domain.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 (notification).
 *
 * <p>상황: 매칭이 끝나거나(매칭됐어요/매칭되지 않았어요), 집결 10분 전, 정산 요청 같은 사건이 생기면
 * 해당 학생에게 알림을 남긴다. 화면정의서 C-04 의 알림은 휴대폰에 뜨는 푸시 알림이고,
 * 누르면 관련 화면으로 이동한다. 알림 권한이 없으면 홈 카드로만 안내한다.
 *
 * <p>알림을 "만드는" send 는 매칭 작업·정산 등 다른 기능에서도 같이 쓴다.
 * 푸시 발송(sent_at 채우기)은 아직 없고, 지금은 알림을 남기고 조회·읽음 처리하는 부분만 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 알림을 남긴다. 같은 dedupeKey 의 알림이 이미 있으면 새로 만들지 않는다.
     * 호출한 쪽의 트랜잭션에 함께 묶여서, 그 작업이 실패하면 알림도 남지 않는다.
     * 예) 매칭 작업: send(new NotificationMessage(학생, 팟, MATCH_RESULT, "match-result:request:301", ...))
     *
     * @return 새로 만들었으면 true, 이미 있던 알림이면 false
     */
    @Transactional
    public boolean send(NotificationMessage message) {
        Instant scheduledAt = (message.scheduledAt() != null) ? message.scheduledAt() : Instant.now();
        int inserted = notificationRepository.insertIfAbsent(
                message.userId(),
                message.partyId(),
                message.type().name(),
                message.dedupeKey(),
                toPayloadJson(message),
                scheduledAt);
        return inserted == 1;
    }

    /**
     * 내 알림 조회 (API-41). 보낼 시각이 지난 알림만, 최신순으로 limit 개씩.
     * - cursor 가 없으면 첫 페이지, 있으면 그다음부터.
     * - 아직 보낼 시각이 안 된 예약 알림(예: 집결 10분 전)은 그 시각이 될 때까지 보이지 않는다.
     */
    public NotificationPage getMyNotifications(Long userId, String cursor, int limit) {
        Instant now = Instant.now();
        PageRequest fetch = PageRequest.of(0, limit + 1);   // 한 개 더 가져와서 다음 페이지 여부 판단
        List<Notification> rows;
        if (cursor == null) {
            rows = notificationRepository.findMyFirstPage(userId, now, fetch);
        } else {
            NotificationCursor c = NotificationCursor.decode(cursor);
            rows = notificationRepository.findMyNextPage(userId, now, c.scheduledAt(), c.id(), fetch);
        }

        boolean hasMore = rows.size() > limit;
        List<Notification> page = hasMore ? rows.subList(0, limit) : rows;
        List<NotificationResponse> notifications = page.stream().map(this::toResponse).toList();
        String nextCursor = hasMore ? NotificationCursor.encode(page.get(page.size() - 1)) : null;
        return new NotificationPage(notifications, nextCursor, hasMore);
    }

    /**
     * 알림 읽음 처리 (API-42). 본인 알림만, 처음 읽은 시각을 남긴다(다시 눌러도 그대로).
     * 남의 알림이거나 없는 알림이면 똑같이 404 RESOURCE_NOT_FOUND.
     */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        notification.markRead(Instant.now());
        return toResponse(notification);
    }

    /** 제목·내용·이동 경로를 payload JSON 으로 만든다. */
    private String toPayloadJson(NotificationMessage message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("title", message.title());
        payload.put("body", message.body());
        payload.put("target", message.target());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("알림 payload 를 만들 수 없습니다.", e);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        try {
            JsonNode payload = objectMapper.readTree(notification.getPayload());
            return NotificationResponse.of(notification, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("알림 payload 를 읽을 수 없습니다. id=" + notification.getId(), e);
        }
    }
}
