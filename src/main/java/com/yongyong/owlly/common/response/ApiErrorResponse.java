package com.yongyong.owlly.common.response;

import java.util.List;

/**
 * 오류 응답 (docs/api.md 1절 "공통 계약").
 * {"error": {"code", "message", "details", "traceId"}}
 * details에는 민감정보·타인 자원 존재 여부를 넣지 않는다.
 */
public record ApiErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, List<String> details, String traceId) {}
}
