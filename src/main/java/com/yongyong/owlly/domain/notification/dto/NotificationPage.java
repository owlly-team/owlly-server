package com.yongyong.owlly.domain.notification.dto;

import java.util.List;

/**
 * 내 알림 한 페이지.
 * nextCursor 를 다음 요청의 cursor 로 보내면 더 이전 알림을 이어서 받는다. 마지막 페이지면 null.
 */
public record NotificationPage(
        List<NotificationResponse> notifications,  // 이번 페이지 알림(최신순)
        String nextCursor,                         // 다음 페이지 책갈피. 없으면 null
        boolean hasMore                            // 더 이전 알림이 있는지
) {}
