package com.yongyong.owlly.domain.settlement.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.domain.settlement.dto.SettlementResponse;
import com.yongyong.owlly.domain.settlement.service.SettlementMemberResolver;
import com.yongyong.owlly.domain.settlement.service.SettlementService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팟 정산 조회 API (API-33).
 *
 * <p>로그인한 학생이 이 팟의 참여자일 때만 들어온다(아니면 404). 응답에 ETag 를 붙여
 * 이후 정산 전 하차 순서 수정 등에서 If-Match 로 쓸 수 있게 한다.
 * 대표는 전체·일반 멤버는 본인 항목만 보는 항목 필터는 Service 정책으로 둔다.
 */
@RestController
@RequestMapping("/api/v1/parties/{partyId}/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementMemberResolver memberResolver;

    /** API-33 예상·선송금·최종 정산 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlement(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId) {
        memberResolver.memberOfParty(partyId, principal.userId()); // 팟 참여자 확인(아니면 404)
        SettlementResponse settlement = settlementService.getSettlement(partyId);
        return ResponseEntity.ok().eTag(ETags.of(settlement)).body(ApiResponse.of(settlement));
    }
}
