package com.yongyong.owlly.domain.settlement.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.settlement.dto.FareInput;
import com.yongyong.owlly.domain.settlement.dto.FarePreview;
import com.yongyong.owlly.domain.settlement.dto.RoutePatch;
import com.yongyong.owlly.domain.settlement.dto.SettlementResponse;
import com.yongyong.owlly.domain.settlement.service.SettlementFareService;
import com.yongyong.owlly.domain.settlement.service.SettlementMemberResolver;
import com.yongyong.owlly.domain.settlement.service.SettlementRouteService;
import com.yongyong.owlly.domain.settlement.service.SettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팟 정산 API (API-33 조회, API-37 미리보기, API-38 확정, API-36 하차 순서 수정).
 *
 * <p>로그인한 학생이 이 팟의 참여자일 때만 들어온다(아니면 404). 조회 응답에 ETag 를 붙여
 * 이후 수정 API(확정·하차 순서)에서 If-Match 로 쓸 수 있게 한다.
 * 대표·일반 멤버 모두 팟 전원의 개인 항목을 본다('정산내역-멤버' 화면이 전원 분담·송금 현황을
 * 보여줌). 타인 계좌번호는 응답 DTO 에 아예 없어 멤버별 필터링은 두지 않는다.
 * 미리보기·확정·하차 순서 수정은 대표만 할 수 있다(아니면 403).
 */
@RestController
@RequestMapping("/api/v1/parties/{partyId}/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementFareService settlementFareService;
    private final SettlementRouteService settlementRouteService;
    private final SettlementMemberResolver memberResolver;

    /** API-33 예상·선송금·최종 정산 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlement(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId) {
        // 팟 참여자 확인(아니면 404). 반환된 멤버 ID 로 본인 항목(isMe)을 표시한다.
        Long viewerMemberId = memberResolver.memberOfParty(partyId, principal.userId());
        SettlementResponse settlement = settlementService.getSettlement(partyId, viewerMemberId);
        return ResponseEntity.ok().eTag(ETags.of(settlement)).body(ApiResponse.of(settlement));
    }

    /** API-37 실제 요금 분담·환급 미리보기. 대표만. 저장하지 않는다. */
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<FarePreview>> preview(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @Valid @RequestBody FareInput input) {
        // 팟 참여자 확인(아니면 404). 대표 여부는 서비스에서 확인(아니면 403).
        Long callerMemberId = memberResolver.memberOfParty(partyId, principal.userId());
        FarePreview preview = settlementFareService.preview(partyId, callerMemberId, input);
        return ResponseEntity.ok(ApiResponse.of(preview));
    }

    /** API-38 실제 요금·개인 부담·환급 확정. 대표만. 환급 PENDING 이체 생성·정산 확정. */
    @PostMapping("/finalize")
    public ResponseEntity<ApiResponse<SettlementResponse>> finalize(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody FareInput input) {
        IdempotencyKey.requireUuid(idempotencyKey);
        // 팟 참여자 확인(아니면 404). 대표 여부는 서비스에서 확인(아니면 403).
        Long callerMemberId = memberResolver.memberOfParty(partyId, principal.userId());
        SettlementResponse settlement = settlementFareService.finalizeSettlement(partyId, callerMemberId, input, ifMatch);
        return ResponseEntity.ok().eTag(ETags.of(settlement)).body(ApiResponse.of(settlement));
    }

    /** API-36 정산 전 실제 하차 순서 수정. 대표만. 경로 재계산·route_version 증가·예상 정산 재계산. */
    @PatchMapping("/route")
    public ResponseEntity<ApiResponse<SettlementResponse>> editRoute(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody RoutePatch patch) {
        IdempotencyKey.requireUuid(idempotencyKey);
        // 팟 참여자 확인(아니면 404). 대표 여부는 서비스에서 확인(아니면 403).
        Long callerMemberId = memberResolver.memberOfParty(partyId, principal.userId());
        SettlementResponse settlement = settlementRouteService.editRoute(partyId, callerMemberId, patch, ifMatch);
        return ResponseEntity.ok().eTag(ETags.of(settlement)).body(ApiResponse.of(settlement));
    }
}
