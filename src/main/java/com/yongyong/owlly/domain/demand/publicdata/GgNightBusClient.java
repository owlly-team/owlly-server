package com.yongyong.owlly.domain.demand.publicdata;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 경기도 시군별 버스 이용객수(경기데이터드림 SIGUNBUSANULUSERCNT) 에서 용인 심야(23시~) 이용객을 읽는다.
 *
 * <p>GET https://openapi.gg.go.kr/SIGUNBUSANULUSERCNT (KEY, Type=json, pIndex, pSize)
 * 응답 row[]: {YY, MT, REGION, TM(시간), WKEND_WKDAY_DIV, ALL_PASNG_ALL_CNT(전체 통행 전체수) ...}.
 * REGION 이 지역 키워드(용인)를 포함하고 TM 이 심야 시작시각 이상인 행의 ALL_PASNG_ALL_CNT 를 합산한다.
 * 키는 환경변수 GG_SERVICE_KEY. openapi.gg.go.kr 는 해외/일부 IP 를 보안정책으로 차단하므로, 국내 서버에서 호출한다.
 */
@Component
public class GgNightBusClient {

    private static final String BASE_URL = "https://openapi.gg.go.kr";
    private static final String SERVICE = "SIGUNBUSANULUSERCNT";

    private final RestClient restClient;
    private final String serviceKey;

    public GgNightBusClient(@Value("${GG_SERVICE_KEY:}") String serviceKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.serviceKey = serviceKey;
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /** 지역 키워드(예: "용인")의 심야(fromHour 시~) 버스 이용객 합. */
    public long fetchNightRiders(String regionKeyword, int fromHour) {
        if (!enabled()) {
            throw new PublicDataException("경기 버스 서비스 키가 없습니다.");
        }
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/" + SERVICE)
                            .queryParam("KEY", serviceKey)
                            .queryParam("Type", "json")
                            .queryParam("pIndex", 1)
                            .queryParam("pSize", 1000)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // IP 차단 시 HTML 이 와서 JSON 파싱이 실패한다 → 공공데이터 예외로 감싼다.
            throw new PublicDataException("경기 버스 호출에 실패했습니다(차단/오류).", e);
        }
        return parseNightRiders(body, regionKeyword, fromHour);
    }

    /** 응답 row 들을 지역·시간대로 걸러 ALL_PASNG_ALL_CNT 를 합산한다(테스트에서 직접 쓸 수 있게 분리). */
    static long parseNightRiders(JsonNode body, String regionKeyword, int fromHour) {
        if (body == null) {
            throw new PublicDataException("경기 버스 응답이 비어 있습니다.");
        }
        JsonNode service = body.path(SERVICE);
        if (!service.isArray()) {
            throw new PublicDataException("경기 버스 응답 형식이 올바르지 않습니다.");
        }
        JsonNode rows = null;
        for (JsonNode part : service) {
            if (part.has("row")) {
                rows = part.path("row");
            } else if (part.has("head")) {
                String code = part.path("head").findPath("CODE").asText("");
                if (!code.isEmpty() && !code.startsWith("INFO-000")) {
                    throw new PublicDataException("경기 버스 응답 오류: " + code);
                }
            }
        }
        if (rows == null || !rows.isArray()) {
            return 0;
        }
        long total = 0;
        for (JsonNode row : rows) {
            String region = row.path("REGION").asText("");
            int hour = parseHour(row.path("TM").asText(""));
            if (region.contains(regionKeyword) && hour >= fromHour) {
                total += row.path("ALL_PASNG_ALL_CNT").asLong(0);
            }
        }
        return total;
    }

    private static int parseHour(String tm) {
        try {
            return Integer.parseInt(tm.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
