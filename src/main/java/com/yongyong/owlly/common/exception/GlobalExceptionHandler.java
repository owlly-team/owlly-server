package com.yongyong.owlly.common.exception;

import com.yongyong.owlly.common.response.ApiErrorResponse;
import com.yongyong.owlly.common.response.ApiErrorResponse.ErrorBody;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 모든 컨트롤러 예외를 docs/api.md 의 오류 형식으로 변환한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스에서 던진 업무 예외. */
    @ExceptionHandler(OwllyException.class)
    public ResponseEntity<ApiErrorResponse> handleOwlly(OwllyException e) {
        return build(e.getErrorCode(), e.getDetails());
    }

    /** @Valid 검증 실패. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(ErrorCode.INVALID_REQUEST, details);
    }

    /** 깨진 JSON·타입 오류·정의에 없는 필드. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.INVALID_REQUEST, List.of());
    }

    /** 스프링 기본 예외(404·405 등)는 상태코드를 유지하고, 그 외 예상 못 한 예외는 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            ErrorCode code = status == HttpStatus.NOT_FOUND
                    ? ErrorCode.RESOURCE_NOT_FOUND
                    : ErrorCode.INVALID_REQUEST;
            return ResponseEntity.status(status).body(body(code, List.of(), newTraceId()));
        }
        String traceId = newTraceId();
        log.error("Unexpected error. traceId={}", traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(ErrorCode.INTERNAL_ERROR, List.of(), traceId));
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, List<String> details) {
        return ResponseEntity.status(code.getStatus()).body(body(code, details, newTraceId()));
    }

    private ApiErrorResponse body(ErrorCode code, List<String> details, String traceId) {
        return new ApiErrorResponse(new ErrorBody(code.name(), code.getMessage(), details, traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
