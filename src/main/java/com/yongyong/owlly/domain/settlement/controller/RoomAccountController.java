package com.yongyong.owlly.domain.settlement.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.settlement.dto.AccountInput;
import com.yongyong.owlly.domain.settlement.dto.MaskedAccount;
import com.yongyong.owlly.domain.settlement.entity.AccountPurpose;
import com.yongyong.owlly.domain.settlement.service.RoomAccountService;
import com.yongyong.owlly.domain.settlement.service.SettlementMemberResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 선송금·환급 수취 계좌 API (API-31).
 *
 * <p>로그인한 학생이 채팅방 참여자일 때만 들어온다. 소유 멤버는 세션 사용자의 해당 팟 참여로 정하고,
 * 용도별 대표/본인 구분·방 만료·이체 잠금은 Service 정책으로 둔다. 계좌번호·예금주는 암호화 저장,
 * 응답은 마스킹된다.
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/accounts")
@RequiredArgsConstructor
public class RoomAccountController {

    private final RoomAccountService roomAccountService;
    private final SettlementMemberResolver memberResolver;

    /** API-31 내 선송금·환급 수취 계좌 등록(교체). */
    @PutMapping("/{purpose}")
    public ResponseEntity<ApiResponse<MaskedAccount>> register(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long roomId,
            @PathVariable AccountPurpose purpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AccountInput input) {
        IdempotencyKey.requireUuid(idempotencyKey);
        Long ownerMemberId = memberResolver.memberOfRoom(roomId, principal.userId());
        MaskedAccount account = roomAccountService.register(roomId, purpose, ownerMemberId, input);
        return ResponseEntity.ok(ApiResponse.of(account));
    }
}
