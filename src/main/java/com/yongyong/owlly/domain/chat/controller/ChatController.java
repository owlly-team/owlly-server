package com.yongyong.owlly.domain.chat.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.chat.dto.*;
import com.yongyong.owlly.domain.chat.service.ChatRoomService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {
    private final ChatRoomService chat;

    @GetMapping("/parties/{partyId}/room")
    @Operation(operationId = "getPartyRoom")
    public ResponseEntity<ApiResponse<RoomResponse>> room(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.of(chat.getRoom(principal.userId(), partyId, Instant.now())));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(operationId = "listMessages")
    public ResponseEntity<MessagePage> messages(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long roomId, @RequestParam(required = false) @Positive Long afterId,
            @RequestParam(required = false) @Positive Long beforeId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(chat.getMessages(principal.userId(), roomId, afterId, beforeId, limit, Instant.now()));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @Operation(operationId = "sendMessage", parameters = @Parameter(name = "X-CSRF-Token", in = ParameterIn.HEADER, required = true,
            description = "로그인 또는 현재 세션 조회 응답의 data.csrfToken"))
    public ResponseEntity<ApiResponse<MessageResponse>> send(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long roomId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody MessageCreate input) {
        IdempotencyKey.requireUuid(key);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(chat.send(principal.userId(), roomId, input, UUID.fromString(key).toString(), Instant.now())));
    }
}
