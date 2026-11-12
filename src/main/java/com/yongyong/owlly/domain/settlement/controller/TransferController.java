package com.yongyong.owlly.domain.settlement.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.settlement.dto.RecipientAccount;
import com.yongyong.owlly.domain.settlement.dto.TransferResponse;
import com.yongyong.owlly.domain.settlement.service.RoomAccountService;
import com.yongyong.owlly.domain.settlement.service.SettlementMemberResolver;
import com.yongyong.owlly.domain.settlement.service.TransferService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 이체(선송금·환급) API (API-32 수취 계좌 조회, API-39 '보냈어요', API-40 '받았어요').
 *
 * <p>로그인한 학생이 이 이체가 속한 팟의 참여자일 때만 들어온다. 송금자/수취자 본인 여부는
 * 세션 사용자의 참여 멤버 ID 로 Service 가 확인한다(본인이 아니면 404). 실제 은행 이체는 하지 않고
 * 상태만 기록하며, 같은 버튼을 다시 눌러도 현재 상태를 그대로 돌려준다(멱등).
 */
@RestController
@RequestMapping("/api/v1/transfers/{transferId}")
@RequiredArgsConstructor
public class TransferController {

    private final RoomAccountService roomAccountService;
    private final TransferService transferService;
    private final SettlementMemberResolver memberResolver;

    /** API-32 송금할 수취 계좌 조회. 송금자에게만 계좌번호 원문을 주고, 응답은 저장하지 않는다. */
    @GetMapping("/recipient-account")
    public ResponseEntity<ApiResponse<RecipientAccount>> recipientAccount(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long transferId) {
        Long payerMemberId = memberResolver.memberOfTransferParty(transferId, principal.userId());
        RecipientAccount account = roomAccountService.getRecipientAccount(payerMemberId, transferId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.of(account));
    }

    /** API-39 송금자의 '보냈어요'. PENDING → SENT. */
    @PostMapping("/sent")
    public ResponseEntity<ApiResponse<TransferResponse>> markSent(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long transferId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotencyKey.requireUuid(idempotencyKey);
        Long payerMemberId = memberResolver.memberOfTransferParty(transferId, principal.userId());
        return ResponseEntity.ok(ApiResponse.of(transferService.markSent(payerMemberId, transferId)));
    }

    /** API-40 수취자의 '받았어요'. PENDING/SENT → RECEIVED. */
    @PostMapping("/received")
    public ResponseEntity<ApiResponse<TransferResponse>> markReceived(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long transferId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotencyKey.requireUuid(idempotencyKey);
        Long payeeMemberId = memberResolver.memberOfTransferParty(transferId, principal.userId());
        return ResponseEntity.ok(ApiResponse.of(transferService.markReceived(payeeMemberId, transferId)));
    }
}
