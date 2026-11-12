package com.yongyong.owlly.domain.transit.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 경기도 버스(GBIS) 노선정보 항목조회에서 노선의 막차 시각을 읽는다.
 *
 * <p>GET https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteInfoItemv2 (routeId, serviceKey, format=json)
 * 막차는 노선 단위로만 있다(기점 출발 upLastTime, 종점 출발 downLastTime, 평일·토·일·공휴일 따로). 정류장별 시각은 없다.
 * 값이 "23:00" 문자열로 오고, 없으면 0·null·빈 값이 섞여 오므로 읽을 수 없는 값은 null 로 둔다.
 */
@Component
public class GbisBusClient {

    private static final String BASE_URL = "https://apis.data.go.kr";

    private final RestClient restClient;
    private final String serviceKey;

    public GbisBusClient(@Value("${GBIS_SERVICE_KEY:}") String serviceKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.serviceKey = serviceKey;
    }

    /**
     * 노선의 막차 시각.
     *
     * @throws GbisException 키 없음, 통신 실패, 오류 응답 등 GBIS 를 쓸 수 없을 때
     */
    public RouteLastTimes findLastTimes(long routeId) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new GbisException("GBIS 서비스 키가 없습니다.");
        }
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/6410000/busrouteservice/v2/getBusRouteInfoItemv2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("routeId", routeId)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new GbisException("GBIS 노선정보 호출에 실패했습니다.", e);
        }
        return parse(routeId, body);
    }

    /** 응답 한 건을 막차 시각으로 바꾼다(테스트에서 직접 쓸 수 있게 분리). */
    static RouteLastTimes parse(long routeId, JsonNode body) {
        JsonNode response = (body == null) ? null : body.path("response");
        if (response == null || response.path("msgHeader").path("resultCode").asInt(-1) != 0) {
            throw new GbisException("GBIS 노선정보 응답이 정상이 아닙니다.");
        }
        JsonNode item = response.path("msgBody").path("busRouteInfoItem");
        if (item.isMissingNode() || item.isNull()) {
            throw new GbisException("GBIS 노선정보가 없습니다. routeId=" + routeId);
        }
        Map<DayType, DayLastTimes> byDay = new EnumMap<>(DayType.class);
        byDay.put(DayType.WEEKDAY, new DayLastTimes(time(item, "upLastTime"), time(item, "downLastTime")));
        byDay.put(DayType.SATURDAY, new DayLastTimes(time(item, "satUpLastTime"), time(item, "satDownLastTime")));
        byDay.put(DayType.SUNDAY, new DayLastTimes(time(item, "sunUpLastTime"), time(item, "sunDownLastTime")));
        byDay.put(DayType.HOLIDAY, new DayLastTimes(time(item, "weUpLastTime"), time(item, "weDownLastTime")));
        return new RouteLastTimes(routeId, item.path("routeName").asText(""), byDay);
    }

    private static LocalTime time(JsonNode item, String field) {
        String text = item.path(field).asText("").trim();
        if (text.matches("\\d{1,2}:\\d{2}")) {
            return LocalTime.parse(text.length() == 4 ? "0" + text : text);
        }
        return null;   // 0, null, 빈 값
    }

    /** 운행 요일 구분. GBIS 는 평일·토·일·공휴일을 따로 준다. */
    public enum DayType { WEEKDAY, SATURDAY, SUNDAY, HOLIDAY }

    /** 한 요일 구분의 막차: 기점에서 출발하는 막차, 종점에서 출발하는 막차. 없으면 null. */
    public record DayLastTimes(
            LocalTime upLast,    // 기점 출발 막차
            LocalTime downLast   // 종점 출발 막차
    ) {}

    /** 노선 하나의 막차 시각 모음. */
    public record RouteLastTimes(
            long routeId,                        // GBIS 노선 번호
            String routeName,                    // 노선 이름. 예) "720-3"
            Map<DayType, DayLastTimes> byDay     // 요일 구분별 막차
    ) {}

    /** GBIS 를 쓸 수 없을 때. 호출한 쪽은 막차 안내만 비우고 나머지는 그대로 응답한다. */
    public static class GbisException extends RuntimeException {
        public GbisException(String message) {
            super(message);
        }

        public GbisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
