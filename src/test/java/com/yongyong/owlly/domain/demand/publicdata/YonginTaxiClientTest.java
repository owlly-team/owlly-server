package com.yongyong.owlly.domain.demand.publicdata;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 용인택시 응답 파싱 검증(실제 응답 구조 기준: 2024-01 처인구 샘플). */
class YonginTaxiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test void sumsRequestMatchedArrivalAcrossEupMyunDong() throws Exception {
        JsonNode body = mapper.readTree("""
                {"resultCode":200,"totalCount":2,"items":[
                  {"siGunGu":"용인시 처인구","eupMyunDong":"김량장동","requestCount":4184,"matchedCount":2512,"arrivalCount":2907},
                  {"siGunGu":"용인시 처인구","eupMyunDong":"고림동","requestCount":3451,"matchedCount":2285,"arrivalCount":1328}]}""");
        var stats = YonginTaxiClient.parse(body);
        assertThat(stats.requestCount()).isEqualTo(7635);
        assertThat(stats.matchedCount()).isEqualTo(4797);
        assertThat(stats.arrivalCount()).isEqualTo(4235);
    }

    @Test void rejectsNonSuccessResult() throws Exception {
        JsonNode body = mapper.readTree("{\"resultCode\":400,\"resultMsg\":\"INVALID_REQUEST\"}");
        assertThatThrownBy(() -> YonginTaxiClient.parse(body)).isInstanceOf(PublicDataException.class);
    }
}
