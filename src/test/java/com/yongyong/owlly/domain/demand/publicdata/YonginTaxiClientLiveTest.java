package com.yongyong.owlly.domain.demand.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 용인택시 공공데이터 라이브 스모크 테스트. 환경변수 YONGIN_TAXI_SERVICE_KEY 가 있을 때만 실행된다(CI 기본 스킵).
 * 실제 엔드포인트가 살아 있고 키·파싱이 맞는지 확인한다.
 */
@EnabledIfEnvironmentVariable(named = "YONGIN_TAXI_SERVICE_KEY", matches = ".+")
class YonginTaxiClientLiveTest {

    @Test void fetchesRealYonginTaxiStatistics() {
        YonginTaxiClient client = new YonginTaxiClient(System.getenv("YONGIN_TAXI_SERVICE_KEY"));
        var stats = client.fetch(2024, 1);
        assertThat(stats.requestCount()).isPositive();
        assertThat(stats.matchedCount()).isPositive();
        assertThat(stats.matchedCount()).isLessThanOrEqualTo(stats.requestCount()); // 탑승 ≤ 호출
        System.out.println("[LIVE] 용인택시 2024-01 호출=" + stats.requestCount()
                + " 탑승=" + stats.matchedCount() + " 도착=" + stats.arrivalCount());
    }
}
