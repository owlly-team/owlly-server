package com.yongyong.owlly.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 업무·공통 오류 코드. 이름과 HTTP 상태는 docs/api.md 6절 "공통 오류" 표를 따른다.
 * 프론트는 문구가 아니라 code 로 분기하므로 이름을 바꾸지 않는다.
 * 여러 명이 함께 수정하는 파일이므로 각자 자기 도메인 블록 아래에만 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "요청 값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 Idempotency-Key로 다른 요청이 들어왔습니다."),
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, "자원이 변경되었습니다. 다시 조회해 주세요."),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "If-Match 헤더가 필요합니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    POLICY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "필요한 기획 정책/운영 설정이 아직 준비되지 않았습니다."),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 경로·교통 데이터를 일시적으로 사용할 수 없습니다."),

    // ===== 학교 이메일 인증 =====
    EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "허용되지 않은 학교 이메일입니다."),
    EMAIL_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 메일을 발송할 수 없습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.UNPROCESSABLE_ENTITY, "인증번호가 올바르지 않습니다."),
    VERIFICATION_EXPIRED(HttpStatus.GONE, "인증 유효기간이 만료되었습니다."),
    VERIFICATION_USED(HttpStatus.CONFLICT, "이미 사용되었거나 무효화된 인증번호입니다."),

    // ===== 학생 가입 =====
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입한 이메일입니다."),
    INVALID_GENDER_CODE(HttpStatus.UNPROCESSABLE_ENTITY, "허용되지 않은 성별 코드입니다."),

    // ===== 학생 로그인 =====
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "가입된 학생 계정이 없습니다."),

    // ===== 관계자 로그인 =====
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "메일이나 비밀번호가 맞지 않아요."),

    // ===== 귀가 (journey) =====
    ACTIVE_JOURNEY_CONFLICT(HttpStatus.CONFLICT, "진행 중인 귀가가 있습니다."),
    ACTIVE_REQUEST_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 동승 신청이 있습니다."),
    INVALID_DEPARTURE_SLOT(HttpStatus.UNPROCESSABLE_ENTITY, "선택할 수 없는 출발 시간대입니다."),
    LOCATION_UNRESOLVABLE(HttpStatus.UNPROCESSABLE_ENTITY, "하차 위치의 행정동을 찾을 수 없습니다."),
    JOURNEY_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 결과가 확정된 귀가입니다."),
    PARTY_LEAVE_REQUIRED(HttpStatus.CONFLICT, "매칭된 팟에서 먼저 나가야 합니다."),
    REQUEST_ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 팟에 배정된 신청입니다. 팟에서 나가기로 취소해 주세요."),
    INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 요청입니다."),

    // ===== 동승 팟 (party) =====
    CONFIRMATION_DEADLINE_PASSED(HttpStatus.CONFLICT, "동승 확정 마감이 지났습니다."),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.CONFLICT, "출발 15분 전이 지나 팟에서 빠질 수 없습니다."),
    FUNDS_ALREADY_IN_FLIGHT(HttpStatus.CONFLICT, "이미 송금이 오간 팟이라 바로 빠질 수 없습니다."),
    BOARDING_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 탑승 여부를 기록할 수 없습니다."),
    BOARDING_ALREADY_RECORDED(HttpStatus.CONFLICT, "이미 기록한 탑승 여부는 바꿀 수 없습니다."),

    // ===== 팟 채팅 =====
    ROOM_NOT_READY(HttpStatus.CONFLICT, "아직 채팅방에 입장할 수 없습니다."),
    ROOM_ACCESS_EXPIRED(HttpStatus.GONE, "채팅방 이용이 종료되었습니다."),
    ROOM_CLOSED(HttpStatus.CONFLICT, "채팅방이 닫혔습니다."),
    MESSAGE_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "보낼 수 없는 메시지입니다."),

    // ===== 안전 신고 =====
    INVALID_REPORT_TARGET(HttpStatus.UNPROCESSABLE_ENTITY, "신고할 수 없는 대상 또는 메시지입니다."),
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 방에서 이미 신고한 대상입니다."),

    // ===== 정산 (settlement) =====
    SETTLEMENT_NOT_READY(HttpStatus.CONFLICT, "아직 정산이 생성되지 않았습니다."),
    ACCOUNT_NOT_REGISTERED(HttpStatus.CONFLICT, "수취 계좌가 아직 등록되지 않았습니다."),
    ACCOUNT_EXPIRED(HttpStatus.GONE, "수취 계좌가 만료되었습니다."),
    TRANSFER_CANCELLED(HttpStatus.CONFLICT, "취소된 이체입니다."),
    FARE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "실제 요금이 예상 요금의 1.2배를 넘을 수 없습니다."),
    ROUTE_VERSION_CONFLICT(HttpStatus.CONFLICT, "경로 버전이 최신과 달라 다시 확인해야 합니다."),
    INVALID_ROUTE_DISTANCE(HttpStatus.CONFLICT, "구간 거리를 계산할 수 없어 분담액을 낼 수 없습니다."),
    SETTLEMENT_FINALIZED(HttpStatus.CONFLICT, "이미 확정된 정산은 바꿀 수 없습니다."),
    PREPAY_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 수령 확인이 끝나지 않은 선송금이 있어요."),
    INVALID_STOP_ORDER(HttpStatus.UNPROCESSABLE_ENTITY, "하차 순서는 현재 멤버 전원을 한 번씩 담아야 합니다."),
    REPRESENTATIVE_CHANGE_REQUIRES_POLICY(HttpStatus.CONFLICT, "마지막 하차자(대표) 변경은 정책 확정 전까지 할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
