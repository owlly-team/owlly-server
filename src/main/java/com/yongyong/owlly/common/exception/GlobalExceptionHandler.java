package com.yongyong.owlly.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.yongyong.owlly.common.response.ApiErrorResponse;
import com.yongyong.owlly.common.response.ApiErrorResponse.ErrorBody;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 컨트롤러 예외를 docs/api.md 의 오류 형식 {"error": {...}} 으로 변환한다.
 * 코드·상태는 docs/api.md 6절을 따른다.
 * details 에는 필드·파라미터 이름만 넣고 사용자가 보낸 값은 넣지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스에서 던진 업무 예외. */
    @ExceptionHandler(OwllyException.class)
    public ResponseEntity<ApiErrorResponse> handleOwlly(OwllyException e) {
        if (e instanceof com.yongyong.owlly.domain.verification.service.VerificationRateLimitException limit) {
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .header("Retry-After", Long.toString(limit.getRetryAfterSeconds()))
                    .body(body(e.getErrorCode(), e.getDetails(), newTraceId()));
        }
        return build(e.getErrorCode(), e.getDetails());
    }

    /** 요청 본문(@Valid) 검증 실패. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, details);
    }

    /** 쿼리·경로 파라미터 검증(@Positive 등) 실패. 예: universityId=-1 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParamValidation(HandlerMethodValidationException e) {
        List<String> details = e.getParameterValidationResults().stream()
                .map(r -> r.getMethodParameter().getParameterName() + ": "
                        + r.getResolvableErrors().stream()
                                .map(err -> err.getDefaultMessage())
                                .collect(Collectors.joining(", ")))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, details);
    }

    /** 숫자 자리에 문자가 오는 등 파라미터 형식 오류. 예: universityId=abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.VALIDATION_ERROR, List.of(e.getName() + ": 형식이 올바르지 않습니다."));
    }

    /** 필수 쿼리 파라미터 누락. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return build(ErrorCode.VALIDATION_ERROR, List.of(e.getParameterName() + ": 필수 값입니다."));
    }

    /** 필수 헤더 누락. If-Match 는 428, 그 외(Idempotency-Key 등)는 422. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if ("If-Match".equalsIgnoreCase(e.getHeaderName())) {
            return build(ErrorCode.PRECONDITION_REQUIRED, List.of());
        }
        return build(ErrorCode.VALIDATION_ERROR, List.of(e.getHeaderName() + ": 필수 헤더입니다."));
    }

    /**
     * 요청 본문을 읽지 못함.
     * - 정의에 없는 필드·필드 타입 오류 → 422 VALIDATION_ERROR (docs/api.md 1절)
     * - 깨진 JSON·본문 없음 → 400 INVALID_REQUEST (docs/api.md 6절)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getMostSpecificCause() instanceof MismatchedInputException mismatch) {
            String field = fieldPath(mismatch);
            String reason = (mismatch instanceof UnrecognizedPropertyException)
                    ? "정의되지 않은 필드입니다."
                    : "형식이 올바르지 않습니다.";
            List<String> details = field.isEmpty() ? List.of() : List.of(field + ": " + reason);
            return build(ErrorCode.VALIDATION_ERROR, details);
        }
        return build(ErrorCode.INVALID_REQUEST, List.of());
    }

    /** 메서드 보안(@PreAuthorize 등)에서 권한 없음. 필터 단계 401/403 은 인증 설정에서 처리한다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return build(ErrorCode.FORBIDDEN, List.of());
    }

    /** 메서드 보안에서 인증 없음. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException e) {
        return build(ErrorCode.UNAUTHENTICATED, List.of());
    }

    /**
     * 그 밖의 예외.
     * 스프링 기본 예외(404·405 등)는 상태코드를 유지하고, 예상 못 한 예외는 500 + 로그에 traceId.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            if (status.value() == HttpStatus.NOT_FOUND.value()) {
                return ResponseEntity.status(status).body(body(ErrorCode.RESOURCE_NOT_FOUND, List.of(), newTraceId()));
            }
            if (status.is4xxClientError()) {
                return ResponseEntity.status(status).body(body(ErrorCode.INVALID_REQUEST, List.of(), newTraceId()));
            }
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

    /** JSON 경로를 "a.b" 형태로. 값은 넣지 않는다. */
    private String fieldPath(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
