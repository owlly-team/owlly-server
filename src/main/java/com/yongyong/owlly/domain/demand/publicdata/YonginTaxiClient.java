package com.yongyong.owlly.domain.demand.publicdata;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 용인앱택시 운행현황(공공데이터포털 15125676) 월별 읍·면·동 통계를 읽는다.
 *
 * <p>GET https://apis.data.go.kr/4050000/yonginTaxiStatistics/driveStatistics (serviceKey, year, month)
 * 응답 items[]: {siGunGu, eupMyunDong, requestCount(호출), matchedCount(탑승), arrivalCount(도착)}.
 * 읍·면·동 전체를 합산해 월 호출·탑승을 낸다(미충족 비율 = (호출−탑승)/호출 의 재료).
 * 키는 환경변수 YONGIN_TAXI_SERVICE_KEY. 실제 호출로 응답 구조를 확인했다(2024-01 처인구 등).
 */
@Component
public class YonginTaxiClient {

    private static final String BASE_URL = "https://apis.data.go.kr";

    private final RestClient restClient;
    private final String serviceKey;

    public YonginTaxiClient(@Value("${YONGIN_TAXI_SERVICE_KEY:}") String serviceKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.serviceKey = serviceKey;
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /** 해당 연·월의 용인 전체 택시 호출·탑승·도착 합. */
    public TaxiMonthlyStats fetch(int year, int month) {
        if (!enabled()) {
            throw new PublicDataException("용인택시 서비스 키가 없습니다.");
        }
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/4050000/yonginTaxiStatistics/driveStatistics")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("year", year)
                            .queryParam("month", month)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new PublicDataException("용인택시 호출에 실패했습니다.", e);
        }
        return parse(body);
    }

    /** 응답을 월 합계로 바꾼다(테스트에서 직접 쓸 수 있게 분리). */
    static TaxiMonthlyStats parse(JsonNode body) {
        if (body == null || body.path("resultCode").asInt(-1) != 200) {
            throw new PublicDataException("용인택시 응답이 정상이 아닙니다.");
        }
        long request = 0, matched = 0, arrival = 0;
        for (JsonNode item : body.path("items")) {
            request += item.path("requestCount").asLong();
            matched += item.path("matchedCount").asLong();
            arrival += item.path("arrivalCount").asLong();
        }
        return new TaxiMonthlyStats(request, matched, arrival);
    }

    public record TaxiMonthlyStats(long requestCount, long matchedCount, long arrivalCount) {}
}
