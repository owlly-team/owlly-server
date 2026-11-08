package com.yongyong.owlly.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 성공 응답 래퍼 (docs/api.md 1절 "공통 계약").
 * 단건·목록 모두 {"data": ...}, 페이지가 있으면 {"data": [...], "meta": {...}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, PageMeta meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(data, meta);
    }

    /** cursor 기반 페이지 정보. */
    public record PageMeta(String nextCursor, boolean hasMore) {}
}
