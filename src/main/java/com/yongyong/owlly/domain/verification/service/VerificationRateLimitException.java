package com.yongyong.owlly.domain.verification.service;

import com.yongyong.owlly.common.exception.RateLimitException;

public class VerificationRateLimitException extends RateLimitException {
    public VerificationRateLimitException(long retryAfterSeconds) {
        super(retryAfterSeconds);
    }
}
