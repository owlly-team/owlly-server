package com.yongyong.owlly.domain.settlement.entity;

/** 개인별 정산 항목 상태. */
public enum SettlementItemStatus {
    ESTIMATED,
    PREPAID,
    REFUND_PENDING,
    COMPLETED,
    REVIEW_REQUIRED
}
