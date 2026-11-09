package com.yongyong.owlly.domain.verification.entity;

/** SMTP 결과가 불확실한 경우 재발송하지 않는다. */
public enum DeliveryStatus { PENDING, SENT, UNKNOWN }
