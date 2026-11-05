package com.yongyong.owlly.domain.settlement.entity;

/** 팟 정산 상태. 음수 환급 등 검토 필요 시 REVIEW_REQUIRED. */
public enum SettlementStatus {
    ESTIMATED,
    PREPAYING,
    AWAITING_FARE,
    REFUNDING,
    COMPLETED,
    REVIEW_REQUIRED
}
