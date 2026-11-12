package com.yongyong.owlly.domain.region.dto;

/**
 * 좌표로 판별한 행정구역 (서버 안에서 쓰는 결과).
 * 예) 수원 영통 좌표 → dongId 105(영통3동), districtId 21(영통구)
 *     오산 좌표 → dongId 130(중앙동), districtId 30(오산시, 구가 없어서 시)
 */
public record ResolvedArea(
        Long dongId,      // 하차 행정동 (administrative_areas.id, 단계 DONG)
        Long districtId   // 같은 방향 매칭 권역: 구, 구가 없는 시는 시 (administrative_areas.id)
) {}
