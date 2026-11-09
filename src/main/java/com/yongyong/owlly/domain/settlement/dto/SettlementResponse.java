package com.yongyong.owlly.domain.settlement.dto;

import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import java.time.Instant;
import java.util.List;

/**
 * 팟 정산 응답 (docs/api.md 5절 Settlement).
 *
 * <p>API-33 조회 결과. "이 팟 택시비가 누가 얼마 내고 얼마 돌려받는지"를 담는다.
 * 3단 구조: 정산 1건 → 개인별 항목(items) 여러 개 → 항목마다 송금(transfers) 여러 개.
 *
 * <p>엔티티를 그대로 내보내지 않고 화면에 필요한 값만 담는다.
 * id 류는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 * 금액 중 '확정 전 NULL' 인 값(actual/refund)은 int 가 아니라 Integer 로 둬서 null 을 허용한다.
 */
public record SettlementResponse(
        Long id,                     // 정산 ID
        Long partyId,                // 팟 ID
        int estimatedTotalWon,       // 전체 예상 요금
        Integer actualTotalWon,      // 대표가 입력한 실제 요금. 확정 전 null
        int routeVersion,            // 분담 계산에 쓴 하차 경로 버전
        String status,               // ESTIMATED / PREPAYING / REFUNDING / COMPLETED ...
        List<Item> items,            // 개인별 정산 항목들
        boolean receiptAvailable,    // 영수증 첨부 여부
        Instant finalizedAt,         // 정산 확정 시각. 확정 전 null
        Instant refundDueAt,         // 환급 기한. 확정 전 null
        List<String> allowedActions  // 서버가 계산한 현재 가능한 동작 (지금은 빈 목록)
) {

    public static SettlementResponse from(Settlement s, List<Item> items) {
        return new SettlementResponse(
                s.getId(),
                s.getPartyId(),
                s.getEstimatedTotalWon(),
                s.getActualTotalWon(),
                s.getRouteVersion(),
                s.getStatus().name(),               // enum -> 문자열
                items,
                s.getReceiptObjectKey() != null,    // 파일 키가 있으면 영수증 있음
                s.getFinalizedAt(),
                s.getRefundDueAt(),
                List.of());                         // allowedActions: 서버 상태 계산은 추후 (지금 빈 목록)
    }

    /** 개인별 정산 한 줄 (docs/api.md 5절 SettlementItem). */
    public record Item(
            Long id,                    // 개인 정산 항목 ID
            Long memberId,              // 부담 멤버(팟 참여 ID)
            int estimatedShareWon,      // 예상 부담액
            int prepayDueWon,           // 선송금해야 할 금액. 대표는 0
            Integer actualShareWon,     // 실제 요금 기준 부담액. 확정 전 null
            Integer refundDueWon,       // 환급액. 대표는 0. 확정 전 null
            String status,              // ESTIMATED / PREPAID / REFUND_PENDING / COMPLETED ...
            List<TransferResponse> transfers  // 이 항목에 걸린 송금들(공용 DTO 재사용)
    ) {

        /** 항목 엔티티 + 그 항목에 속한 이체 목록을 묶어 응답 한 줄로 만든다. */
        public static Item from(SettlementItem item, List<TransferResponse> transfers) {
            return new Item(
                    item.getId(),
                    item.getMemberId(),
                    item.getEstimatedShareWon(),
                    item.getPrepayDueWon(),
                    item.getActualShareWon(),
                    item.getRefundDueWon(),
                    item.getStatus().name(),
                    transfers);
        }
    }
}
