package com.yongyong.owlly.domain.fare.service;

import com.yongyong.owlly.domain.fare.client.KakaoMobilityClient;
import com.yongyong.owlly.domain.fare.client.KakaoMobilityClient.KakaoRoute;
import com.yongyong.owlly.domain.fare.client.KakaoMobilityClient.KakaoRouteException;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 택시 예상 거리·요금 (택시 요금 계산).
 *
 * <p>상황: 매칭 작업이 팟의 하차 순서를 정하면 전체 예상 택시비와 구간 거리를 계산한다.
 * 막차·동승·혼자 택시 비교의 "혼자 택시" 요금도 같은 계산을 쓴다.
 *
 * <p>계산 방법
 * <ul>
 *   <li>카카오모빌리티 실시간 길찾기의 거리·택시 요금·통행료를 쓴다.</li>
 *   <li>카카오 요금에는 심야할증이 들어 있지 않다(23:50 출발로 길찾기를 해도, 새벽에 지금 출발로 불러도 할증 전 요금이 온다).
 *       그래서 출발 시각이 심야(23:00~04:00)이면 호출한 시각과 관계없이 할증률을 직접 곱한다.</li>
 *   <li>카카오를 쓸 수 없으면(키 없음, 장애, 쿼터 초과) 용인시 중형택시 요금표로 대신 계산한다.
 *       거리는 직선 거리 × 1.3 으로 어림한다.</li>
 * </ul>
 * 시계외할증(용인 → 수원 등)은 경계 지점을 알 수 없어 넣지 않는다.
 */
@Slf4j
@Service
public class TaxiFareService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final LocalTime NIGHT_START = LocalTime.of(23, 0);
    static final LocalTime NIGHT_END = LocalTime.of(4, 0);

    /** 직선 거리를 도로 거리로 어림하는 배율. 도보 시간 공식과 같은 값. */
    static final double ROAD_FACTOR = 1.3;

    /** 용인시 중형택시 요금표(2023.7.1~): 1.8km까지 4,800원, 이후 104m마다 100원. */
    static final int BASE_FARE_WON = 4_800;
    static final int BASE_DISTANCE_M = 1_800;
    static final int UNIT_DISTANCE_M = 104;
    static final int UNIT_FARE_WON = 100;

    static final String SOURCE_KAKAO = "KAKAO_DIRECTIONS_V1";
    static final String SOURCE_KAKAO_NIGHT_ADJUSTED = "KAKAO_DIRECTIONS_V1+NIGHT_RATE";
    static final String SOURCE_FARE_TABLE = "YONGIN_FARE_TABLE_2023+STRAIGHT_X1.3";

    private final KakaoMobilityClient kakaoMobilityClient;
    private final double nightSurchargeRate;

    public TaxiFareService(KakaoMobilityClient kakaoMobilityClient,
                           @Value("${owlly.fare.night-surcharge-rate:0.3}") double nightSurchargeRate) {
        this.kakaoMobilityClient = kakaoMobilityClient;
        this.nightSurchargeRate = nightSurchargeRate;
    }

    /**
     * 거점에서 하차 지점들을 순서대로 지나는 경로의 예상 거리·요금.
     *
     * @param origin      출발 거점
     * @param stops       하차 지점(하차 순서대로)
     * @param departureAt 출발 시각. 심야할증 판단에 쓴다
     */
    public RouteEstimate estimateRoute(GeoPoint origin, List<GeoPoint> stops, Instant departureAt) {
        try {
            KakaoRoute route = kakaoMobilityClient.findRoute(origin, stops);
            return fromKakao(route, departureAt);
        } catch (KakaoRouteException e) {
            log.warn("카카오 길찾기를 쓸 수 없어 요금표 공식으로 계산합니다. reason={}", e.getMessage());
            return fromFareTable(origin, stops, departureAt);
        }
    }

    private RouteEstimate fromKakao(KakaoRoute route, Instant departureAt) {
        int taxiFare = route.taxiFareWon();
        String source = SOURCE_KAKAO;
        if (isNight(departureAt)) {
            taxiFare = roundUpToUnit(taxiFare * (1 + nightSurchargeRate));
            source = SOURCE_KAKAO_NIGHT_ADJUSTED;
        }
        return new RouteEstimate(
                List.copyOf(route.sectionDistancesM()),
                route.distanceM(),
                taxiFare + route.tollWon(),
                source);
    }

    private RouteEstimate fromFareTable(GeoPoint origin, List<GeoPoint> stops, Instant departureAt) {
        List<Integer> segments = new ArrayList<>();
        GeoPoint from = origin;
        for (GeoPoint to : stops) {
            segments.add((int) Math.round(straightDistanceM(from, to) * ROAD_FACTOR));
            from = to;
        }
        int total = segments.stream().mapToInt(Integer::intValue).sum();
        return new RouteEstimate(segments, total, fareByTable(total, departureAt), SOURCE_FARE_TABLE);
    }

    /** 용인시 요금표로 계산한 요금. 심야면 할증률을 곱한다. */
    int fareByTable(int distanceM, Instant departureAt) {
        int fare = BASE_FARE_WON;
        if (distanceM > BASE_DISTANCE_M) {
            int units = (distanceM - BASE_DISTANCE_M + UNIT_DISTANCE_M - 1) / UNIT_DISTANCE_M;
            fare += units * UNIT_FARE_WON;
        }
        if (isNight(departureAt)) {
            fare = roundUpToUnit(fare * (1 + nightSurchargeRate));
        }
        return fare;
    }

    /** 심야할증 시간(23:00 ~ 다음 날 04:00, 한국 시간)인지. */
    static boolean isNight(Instant at) {
        LocalTime time = at.atZone(KST).toLocalTime();
        return !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END);
    }

    /** 두 지점 사이 직선 거리(m). */
    public static double straightDistanceM(GeoPoint a, GeoPoint b) {
        double earthRadiusM = 6_371_000;
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earthRadiusM * Math.asin(Math.sqrt(h));
    }

    /** 택시 요금처럼 100원 단위로 올린다. */
    private static int roundUpToUnit(double won) {
        return (int) Math.ceil(won / UNIT_FARE_WON) * UNIT_FARE_WON;
    }
}
