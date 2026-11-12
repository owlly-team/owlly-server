package com.yongyong.owlly.domain.settlement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 정산 전 실제 하차 순서 수정 요청 (docs/api.md 5절 RoutePatch). API-36 요청 본문.
 *
 * @param orderedMemberIds    현재 계산 대상 전원을 한 번씩 담은 하차 순서(2~4명, 중복 불가). 마지막은 대표
 * @param expectedRouteVersion 수정 전 경로 버전. 최신과 다르면 409 ROUTE_VERSION_CONFLICT
 */
public record RoutePatch(
        @NotEmpty(message = "하차 순서는 비어 있을 수 없습니다.")
        @Size(min = 2, max = 4, message = "하차 순서는 2~4명이어야 합니다.")
        List<String> orderedMemberIds,
        @Min(value = 1, message = "경로 버전은 1 이상이어야 합니다.") int expectedRouteVersion) {
}
