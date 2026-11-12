package com.yongyong.owlly.domain.transit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import org.junit.jupiter.api.Test;

/** 도보 시간 어림: 거리 = 직선 × 1.3, 분당 70m (화면정의서 예시 300m = 4분, 850m = 12분). */
class WalkingTimeTest {

    @Test
    void 화면정의서_예시_거리는_같은_분으로_계산된다() {
        assertThat(WalkingTime.minutes(300)).isEqualTo(4);
        assertThat(WalkingTime.minutes(850)).isEqualTo(12);
    }

    @Test
    void 아무리_가까워도_1분() {
        assertThat(WalkingTime.minutes(10)).isEqualTo(1);
        assertThat(WalkingTime.minutes(0)).isEqualTo(1);
    }

    @Test
    void 걷는_거리는_직선_거리의_1점3배() {
        GeoPoint a = new GeoPoint(37.3219, 127.1265);
        GeoPoint b = new GeoPoint(37.3233333, 127.12585);
        double straight = com.yongyong.owlly.domain.fare.service.TaxiFareService.straightDistanceM(a, b);

        assertThat(WalkingTime.distanceM(a, b)).isEqualTo((int) Math.round(straight * 1.3));
    }
}
