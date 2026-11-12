package com.yongyong.owlly.domain.fare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.domain.fare.client.KakaoMobilityClient;
import com.yongyong.owlly.domain.fare.client.KakaoMobilityClient.KakaoRoute;
import com.yongyong.owlly.domain.fare.client.KakaoMobilityClient.KakaoRouteException;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 택시 요금 할증: 심야(출발 시각 기준 30%)와 시계 외(시·군을 넘으면 20%)는 해당하는 것만 더해서 곱한다. */
class TaxiFareSurchargeTest {

    private static final GeoPoint ORIGIN = new GeoPoint(37.3219, 127.1268);
    private static final List<GeoPoint> STOPS = List.of(new GeoPoint(37.2519, 127.0729));
    private static final Instant DAY = Instant.parse("2026-11-10T05:00:00Z");     // 14:00 KST
    private static final Instant NIGHT = Instant.parse("2026-11-10T14:30:00Z");   // 23:30 KST

    private TaxiFareService service(int taxiFare, int toll) {
        KakaoMobilityClient client = mock(KakaoMobilityClient.class);
        when(client.findRoute(any(), anyList())).thenReturn(new KakaoRoute(List.of(21_000), 21_000, taxiFare, toll, 2_040));
        return new TaxiFareService(client, 0.3, 0.2);
    }

    @Test
    void 낮이고_시_안이면_카카오_요금_그대로() {
        assertThat(service(10_000, 0).estimateRoute(ORIGIN, STOPS, DAY, false).totalFareWon()).isEqualTo(10_000);
    }

    @Test
    void 시계_외만_20퍼센트() {
        var estimate = service(10_000, 0).estimateRoute(ORIGIN, STOPS, DAY, true);

        assertThat(estimate.totalFareWon()).isEqualTo(12_000);
        assertThat(estimate.routingSource()).isEqualTo("KAKAO_DIRECTIONS_V1+OUT_OF_CITY_RATE");
        assertThat(estimate.durationSec()).isEqualTo(2_040);
    }

    @Test
    void 심야만_30퍼센트_기존_동작_그대로() {
        var estimate = service(10_000, 0).estimateRoute(ORIGIN, STOPS, NIGHT, false);

        assertThat(estimate.totalFareWon()).isEqualTo(13_000);
        assertThat(estimate.routingSource()).isEqualTo(TaxiFareService.SOURCE_KAKAO_NIGHT_ADJUSTED);
    }

    @Test
    void 심야이고_시계_외면_할증률을_더해_50퍼센트_통행료는_할증_제외() {
        var estimate = service(10_000, 500).estimateRoute(ORIGIN, STOPS, NIGHT, true);

        assertThat(estimate.totalFareWon()).as("10,000 × 1.5 + 통행료 500").isEqualTo(15_500);
        assertThat(estimate.routingSource()).isEqualTo("KAKAO_DIRECTIONS_V1+NIGHT_RATE+OUT_OF_CITY_RATE");
    }

    @Test
    void 인자_세_개_호출은_시계_외를_붙이지_않는다() {
        assertThat(service(10_000, 0).estimateRoute(ORIGIN, STOPS, DAY).totalFareWon()).isEqualTo(10_000);
    }

    @Test
    void 카카오를_못_쓰면_요금표_공식에도_시계_외를_붙인다() {
        KakaoMobilityClient client = mock(KakaoMobilityClient.class);
        when(client.findRoute(any(), anyList())).thenThrow(new KakaoRouteException("장애"));
        TaxiFareService fallback = new TaxiFareService(client, 0.3, 0.2);

        assertThat(fallback.fareByTable(1_800, DAY, false)).isEqualTo(4_800);
        assertThat(fallback.fareByTable(1_800, DAY, true)).as("4,800 × 1.2 올림").isEqualTo(5_800);
        assertThat(fallback.fareByTable(1_800, NIGHT, true)).as("4,800 × 1.5").isEqualTo(7_200);
        assertThat(fallback.estimateRoute(ORIGIN, STOPS, DAY, true).routingSource()).isEqualTo(TaxiFareService.SOURCE_FARE_TABLE);
    }
}
