package com.yongyong.owlly.domain.journey.entity;

/** 매칭 신청 상태. WAITING -> MATCHED / FAILED / CANCELLED / EXPIRED. */
public enum RideRequestStatus {
    WAITING,
    MATCHED,
    FAILED,
    CANCELLED,
    EXPIRED
}
