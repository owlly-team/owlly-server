package com.yongyong.owlly.domain.settlement.dto;

import java.util.List;

/**
 * 실제 요금 분담·환급 미리보기 (docs/api.md 5절 FarePreview). API-37 응답. 저장하지 않는 계산 결과.
 *
 * @param actualTotalWon  입력한 실제 요금(원)
 * @param routeVersion    계산에 쓴 경로 버전
 * @param items           멤버별 예상 최종 부담·차액
 * @param canFinalize     이 입력으로 확정할 수 있는지
 * @param blockingReasons 확정 불가 사유 코드(없으면 빈 목록)
 */
public record FarePreview(
        int actualTotalWon,
        int routeVersion,
        List<FarePreviewItem> items,
        boolean canFinalize,
        List<String> blockingReasons) {
}
