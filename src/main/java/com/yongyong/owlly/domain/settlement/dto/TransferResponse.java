package com.yongyong.owlly.domain.settlement.dto;

import com.yongyong.owlly.domain.settlement.entity.Transfer;
import java.time.Instant;

/**
 * 수동 송금 한 건 응답 (docs/api.md 5절 Transfer).
 *
 * <p>API-39·40(보냈어요/받았어요)의 단건 응답이자, API-33 정산 조회의 항목 안에도 들어간다.
 * 같은 모양을 두 곳에서 쓰므로 공용 DTO 로 둔다.
 */
public record TransferResponse(
        Long id,
        Long settlementItemId,
        String kind,            // PREPAY(선송금) / REFUND(환급)
        Long payerMemberId,     // 보내는 사람
        Long payeeMemberId,     // 받는 사람
        int amountWon,          // 금액
        String status,          // PENDING / SENT / RECEIVED / CANCELLED
        Instant sentAt,         // '보냈어요' 시각. 아직이면 null
        Instant receivedAt      // '받았어요' 시각. 아직이면 null
) {

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getSettlementItemId(),
                t.getKind().name(),
                t.getPayerMemberId(),
                t.getPayeeMemberId(),
                t.getAmountWon(),
                t.getStatus().name(),
                t.getSentAt(),
                t.getReceivedAt());
    }
}
