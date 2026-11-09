package com.yongyong.owlly.domain.settlement.dto;

/**
 * 매칭 직후 계산한 팟 참여자 한 명의 예상 금액.
 * 예) 김민준: 예상 1인 분담액 5,000원, 대표에게 보낼 금액 6,000원.
 */
public record MemberEstimate(
        Long memberId,            // 팟 참여자 (party_members.id)
        int estimatedShareWon,    // 예상 1인 분담액(원)
        int prepayDueWon          // 대표에게 보낼 금액(선송금, 원). 대표는 0
) {}
