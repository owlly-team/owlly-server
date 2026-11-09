package com.yongyong.owlly.domain.fare.dto;

/**
 * 지도 위 한 지점(위도·경도).
 * 예) 출발 거점, 학생의 하차 위치.
 */
public record GeoPoint(
        double latitude,   // 위도
        double longitude   // 경도
) {}
