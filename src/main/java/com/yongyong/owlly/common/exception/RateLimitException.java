package com.yongyong.owlly.common.exception;

import lombok.Getter;

/** 인증 기능에서 공통으로 사용하는 Retry-After 포함 오류. */
@Getter
public class RateLimitException extends OwllyException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
