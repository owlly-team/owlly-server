package com.yongyong.owlly.domain.settlement.dto;

/**
 * 실제 요금 기준 멤버별 예상 최종 부담 (docs/api.md 5절 FarePreviewItem).
 *
 * @param memberId       팟 참여자(party_members.id)
 * @param actualShareWon 거리 비례로 계산한 실제 분담액(원)
 * @param refundDueWon   차액 = 선송금 − 확정 분담. 양수면 대표가 돌려주고, 음수면 멤버가 더 보냄. 대표는 0
 */
public record FarePreviewItem(String memberId, int actualShareWon, int refundDueWon) {
}
