package com.yongyong.owlly.domain.notification.dto;

import com.yongyong.owlly.domain.notification.entity.NotificationType;
import java.time.Instant;

/**
 * 알림을 만들 때 넘기는 내용. 매칭 작업·정산 등 알림을 보내는 쪽이 채운다.
 *
 * <p>예) 매칭 성공 알림
 * new NotificationMessage(학생id, 팟id, MATCH_RESULT, "match-result:request:301",
 *         "매칭됐어요", "23:30 단국대 정문 출발 · 22:49까지 확정해 주세요", "/parties/401", null)
 *
 * <p>제목·내용에는 실명·정확한 하차 위치·계좌를 넣지 않는다(ERD notifications 규칙).
 */
public record NotificationMessage(
        Long userId,              // 받는 학생
        Long partyId,             // 관련 팟. 팟과 무관한 알림이면 null
        NotificationType type,    // MATCH_RESULT / PREPAY / REFUND / SYSTEM
        String dedupeKey,         // 같은 사건이면 같은 값 → 알림이 한 번만 생김. 예) "match-result:request:301"
        String title,             // 알림 제목. 예) "매칭됐어요"
        String body,              // 알림 내용 요약
        String target,            // 누르면 이동할 앱 화면 경로. 예) "/parties/401"
        Instant scheduledAt       // 보낼 시각. null 이면 지금. 예) 집결 10분 전 알림은 미래 시각
) {}
