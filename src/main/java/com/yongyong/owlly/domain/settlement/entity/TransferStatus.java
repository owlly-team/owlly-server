package com.yongyong.owlly.domain.settlement.entity;

/** 외부 수동 이체 상태. PENDING -> SENT -> RECEIVED (또는 CANCELLED). */
public enum TransferStatus {
    PENDING,
    SENT,
    RECEIVED,
    CANCELLED
}
