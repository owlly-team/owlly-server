package com.yongyong.owlly.domain.notification.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.notification.dto.NotificationPage;
import com.yongyong.owlly.domain.notification.dto.NotificationResponse;
import com.yongyong.owlly.domain.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림 API. 로그인한 사용자 본인 알림만 다룬다.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * API-41 내 알림 조회. 최신순 limit 개씩, 다음 페이지는 meta.nextCursor 로.
     */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        NotificationPage page = notificationService.getMyNotifications(principal.userId(), cursor, limit);
        return ApiResponse.of(page.notifications(), new ApiResponse.PageMeta(page.nextCursor(), page.hasMore()));
    }

    /**
     * API-42 알림 읽음 처리. 이미 읽었으면 처음 읽은 시각을 유지해 같은 키로 다시 보내도 안전하다.
     */
    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long notificationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return ApiResponse.of(notificationService.markRead(principal.userId(), notificationId));
    }
}
