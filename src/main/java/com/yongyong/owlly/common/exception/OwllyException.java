package com.yongyong.owlly.common.exception;

import java.util.List;
import lombok.Getter;

/**
 * 서비스 계층에서 업무 오류를 알릴 때 던지는 예외.
 * 응답 형식은 GlobalExceptionHandler 가 만든다.
 *
 * <pre>
 * throw new OwllyException(ErrorCode.JOURNEY_ALREADY_RESOLVED);
 * </pre>
 */
@Getter
public class OwllyException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<String> details;

    public OwllyException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public OwllyException(ErrorCode errorCode, List<String> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
}
