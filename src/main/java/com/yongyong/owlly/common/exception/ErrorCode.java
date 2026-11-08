package com.yongyong.owlly.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 업무·공통 오류 코드. docs/api.md 의 "업무 오류" 표기와 이름을 맞춘다.
 * 여러 명이 함께 수정하는 파일이므로 각자 자기 도메인 블록 아래에만 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_REQUEST(HttpStatus.UNPROCESSABLE_ENTITY, "요청 값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 Idempotency-Key로 다른 요청이 들어왔습니다."),
    POLICY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "필요한 기획 정책/운영 설정이 아직 준비되지 않았습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ===== 귀가 (journey) =====
    ACTIVE_JOURNEY_CONFLICT(HttpStatus.CONFLICT, "진행 중인 귀가가 있습니다."),
    LOCATION_UNRESOLVABLE(HttpStatus.UNPROCESSABLE_ENTITY, "하차 위치의 행정동을 찾을 수 없습니다."),
    JOURNEY_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 결과가 확정된 귀가입니다."),
    PARTY_LEAVE_REQUIRED(HttpStatus.CONFLICT, "매칭된 팟에서 먼저 나가야 합니다.");

    private final HttpStatus status;
    private final String message;
}
