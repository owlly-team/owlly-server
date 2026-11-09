package com.yongyong.owlly.domain.verification.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import lombok.Getter;

@Getter
public class VerificationRateLimitException extends OwllyException {
    private final long retryAfterSeconds;
    public VerificationRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
