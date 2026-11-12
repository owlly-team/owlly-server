package com.yongyong.owlly.domain.settlement.dto;

/**
 * 수취 계좌 은행 (docs/api.md 5절 Bank).
 *
 * <p>API-03 의 응답 항목. 계좌 등록(API-31)의 {@code bankCode} 는 이 목록의 {@code code} 를 쓴다.
 */
public record Bank(String code, String name) {
}
