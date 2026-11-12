package com.yongyong.owlly.domain.fare.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오모빌리티 자동차 길찾기 호출.
 *
 * <p>GET https://apis-navi.kakaomobility.com/v1/directions (실시간 길찾기).
 * 경유지마다 구간(sections)이 나뉘어 와서 하차 순서별 구간 거리를 얻을 수 있다.
 * 택시 요금(fare.taxi)에는 심야할증이 들어 있지 않다(실측). 출발 시각이 심야이면 서비스에서 할증률을 곱한다.
 */
@Component
public class KakaoMobilityClient {

    private static final String BASE_URL = "https://apis-navi.kakaomobility.com";
    private static final int MAX_WAYPOINTS = 5;

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoMobilityClient(@Value("${KAKAO_REST_API_KEY:}") String restApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.restApiKey = restApiKey;
    }

    /** 키가 설정돼 있는지. 없으면 요금표 공식으로 대신 계산한다. */
    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    /**
     * 출발지에서 경유지들을 순서대로 지나 마지막 지점까지 가는 경로.
     *
     * @param origin 출발지(거점)
     * @param stops  하차 지점들(순서대로). 마지막이 목적지, 나머지는 경유지
     * @throws KakaoRouteException 키 없음, 통신 실패, 길찾기 실패(result_code != 0)
     */
    public KakaoRoute findRoute(GeoPoint origin, List<GeoPoint> stops) {
        if (!isConfigured()) {
            throw new KakaoRouteException("카카오 REST API 키가 없습니다.");
        }
        if (stops.isEmpty() || stops.size() - 1 > MAX_WAYPOINTS) {
            throw new KakaoRouteException("하차 지점 수가 길찾기 범위를 벗어났습니다. size=" + stops.size());
        }
        GeoPoint destination = stops.get(stops.size() - 1);
        String waypoints = stops.subList(0, stops.size() - 1).stream()
                .map(KakaoMobilityClient::toParam)
                .collect(Collectors.joining("|"));

        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uri -> {
                        uri.path("/v1/directions")
                                .queryParam("origin", toParam(origin))
                                .queryParam("destination", toParam(destination))
                                .queryParam("priority", "RECOMMEND")
                                .queryParam("summary", true);
                        if (!waypoints.isEmpty()) {
                            uri.queryParam("waypoints", waypoints);
                        }
                        return uri.build();
                    })
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new KakaoRouteException("카카오 길찾기 호출에 실패했습니다.", e);
        }
        return toRoute(body);
    }

    private static KakaoRoute toRoute(JsonNode body) {
        JsonNode route = (body == null) ? null : body.path("routes").path(0);
        if (route == null || route.isMissingNode() || route.path("result_code").asInt(-1) != 0) {
            String message = (route == null) ? "응답 없음" : route.path("result_msg").asText("응답 형식 다름");
            throw new KakaoRouteException("카카오 길찾기 실패: " + message);
        }
        JsonNode summary = route.path("summary");
        List<Integer> sectionDistances = new ArrayList<>();
        for (JsonNode section : route.path("sections")) {
            sectionDistances.add(section.path("distance").asInt());
        }
        return new KakaoRoute(
                sectionDistances,
                summary.path("distance").asInt(),
                summary.path("fare").path("taxi").asInt(),
                summary.path("fare").path("toll").asInt(),
                summary.path("duration").asInt());
    }

    /** 카카오 좌표 형식은 "경도,위도" 순서다. */
    private static String toParam(GeoPoint point) {
        return point.longitude() + "," + point.latitude();
    }

    /** 카카오 길찾기 결과 중 쓰는 값. */
    public record KakaoRoute(
            List<Integer> sectionDistancesM,  // 경유지로 나뉜 구간별 거리(m)
            int distanceM,                    // 전체 거리(m)
            int taxiFareWon,                  // 택시 요금(원). 심야할증은 들어 있지 않다(출발 시각 기준으로 서비스에서 곱한다)
            int tollWon,                      // 통행료(원)
            int durationSec                   // 예상 소요 시간(초). 모르면 0
    ) {
        public KakaoRoute(List<Integer> sectionDistancesM, int distanceM, int taxiFareWon, int tollWon) {
            this(sectionDistancesM, distanceM, taxiFareWon, tollWon, 0);
        }
    }

    /** 카카오 길찾기를 쓸 수 없을 때. 호출한 쪽은 요금표 공식으로 대신 계산한다. */
    public static class KakaoRouteException extends RuntimeException {
        public KakaoRouteException(String message) {
            super(message);
        }

        public KakaoRouteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
