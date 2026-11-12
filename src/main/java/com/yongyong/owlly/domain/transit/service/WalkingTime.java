package com.yongyong.owlly.domain.transit.service;

import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;

/**
 * 도보 거리·시간 어림 (외부 길찾기를 쓰지 않는다).
 * 거리 = 직선 거리 × 1.3, 시간 = 거리 ÷ 분당 70m. 70m 는 화면정의서 예시(300m = 4분, 850m = 12분)와 맞는 값이다.
 */
public final class WalkingTime {

    static final double ROAD_FACTOR = 1.3;
    static final double METERS_PER_MINUTE = 70.0;

    private WalkingTime() {
    }

    /** 두 지점 사이 걷는 거리(m). 직선 거리에 1.3 배를 해 도로를 따라 걷는 거리로 어림한다. */
    public static int distanceM(GeoPoint from, GeoPoint to) {
        return (int) Math.round(TaxiFareService.straightDistanceM(from, to) * ROAD_FACTOR);
    }

    /** 걷는 거리(m)에 걸리는 분. 아무리 가까워도 1분. */
    public static int minutes(int distanceM) {
        return Math.max(1, (int) Math.round(distanceM / METERS_PER_MINUTE));
    }
}
