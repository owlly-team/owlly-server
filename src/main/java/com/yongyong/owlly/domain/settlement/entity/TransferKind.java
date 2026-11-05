package com.yongyong.owlly.domain.settlement.entity;

/** 이체 종류. 선송금(멤버->대표), 환급(대표->멤버). */
public enum TransferKind {
    PREPAY,
    REFUND
}
