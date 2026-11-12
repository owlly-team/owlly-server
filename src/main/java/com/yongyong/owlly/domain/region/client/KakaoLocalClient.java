package com.yongyong.owlly.domain.region.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로컬 "좌표로 행정구역정보 변환" 호출.
 *
 * <p>GET https://dapi.kakao.com/v2/local/geo/coord2regioncode.json
 * 응답에는 법정동(B)과 행정동(H)이 같이 오는데, 행정동(H)만 쓴다.
 * 예) 경기도 / 수원시 영통구 / 영통3동 / 4111758500
 */
@Component
public class KakaoLocalClient {

    private static final String BASE_URL = "https://dapi.kakao.com";
    private static final String ADMINISTRATIVE_DONG = "H";

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoLocalClient(@Value("${KAKAO_REST_API_KEY:}") String restApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.restApiKey = restApiKey;
    }

    /**
     * 좌표가 속한 행정동. 바다처럼 행정동이 없으면 빈 값.
     *
     * @throws KakaoLocalException 키 없음, 통신 실패, 한도 초과 등 카카오를 쓸 수 없을 때
     */
    public Optional<KakaoRegion> findAdministrativeDong(double latitude, double longitude) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new KakaoLocalException("카카오 REST API 키가 없습니다.");
        }
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", longitude)          // 카카오는 x = 경도
                            .queryParam("y", latitude)           // y = 위도
                            .build())
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new KakaoLocalException("카카오 좌표→행정구역 호출에 실패했습니다.", e);
        }
        if (body == null) {
            throw new KakaoLocalException("카카오 좌표→행정구역 응답이 비어 있습니다.");
        }
        for (JsonNode document : body.path("documents")) {
            if (ADMINISTRATIVE_DONG.equals(document.path("region_type").asText())) {
                return Optional.of(new KakaoRegion(
                        document.path("code").asText(""),
                        document.path("region_1depth_name").asText(""),
                        document.path("region_2depth_name").asText(""),
                        document.path("region_3depth_name").asText("")));
            }
        }
        return Optional.empty();
    }

    /** 행정동 한 건 (카카오 응답 중 쓰는 값). */
    public record KakaoRegion(
            String code,         // 행정동 코드 10자리. 예) "4111758500". 앞 5자리가 시·군·구
            String depth1Name,   // 시·도. 예) "경기도", "서울특별시"
            String depth2Name,   // 구 단위. 예) "수원시 영통구", "서초구", "오산시"(구가 없는 시)
            String depth3Name    // 행정동. 예) "영통3동"
    ) {}

    /** 카카오 로컬을 쓸 수 없을 때. 호출한 쪽은 503 UPSTREAM_UNAVAILABLE 로 응답한다. */
    public static class KakaoLocalException extends RuntimeException {
        public KakaoLocalException(String message) {
            super(message);
        }

        public KakaoLocalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
