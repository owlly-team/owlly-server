package com.yongyong.owlly.domain.user.entity;

/** 계정 상태. SUSPENDED 는 suspended_until / restriction_reason 과 함께 해석. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN
}
