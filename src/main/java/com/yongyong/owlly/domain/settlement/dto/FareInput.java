package com.yongyong.owlly.domain.settlement.dto;

import jakarta.validation.constraints.Min;

/**
 * 실제 요금 입력 (docs/api.md 5절 FareInput). API-37 미리보기·API-38 확정의 요청 본문.
 *
 * @param actualTotalWon 대표가 입력한 실제 택시요금(원). 택시를 못 탔으면 0 이 아니라 별도 흐름
 * @param routeVersion   계산에 쓸 현재 경로 버전. 최신과 다르면 409 ROUTE_VERSION_CONFLICT
 */
public record FareInput(
        @Min(value = 1, message = "실제 요금은 1원 이상이어야 합니다.") int actualTotalWon,
        @Min(value = 1, message = "경로 버전은 1 이상이어야 합니다.") int routeVersion) {
}
