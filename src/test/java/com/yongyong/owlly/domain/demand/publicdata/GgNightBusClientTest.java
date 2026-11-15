package com.yongyong.owlly.domain.demand.publicdata;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 경기데이터드림 버스 이용객수 응답 파싱 검증(지역·심야 시간대 필터·합산). 실제 호출은 IP 차단이라 파싱만 검증. */
class GgNightBusClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test void sumsNightRidersForRegionOnly() throws Exception {
        JsonNode body = mapper.readTree("""
                {"SIGUNBUSANULUSERCNT":[
                  {"head":[{"list_total_count":4},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상"}}]},
                  {"row":[
                    {"REGION":"용인시","TM":"23","ALL_PASNG_ALL_CNT":30},
                    {"REGION":"용인시","TM":"23","ALL_PASNG_ALL_CNT":17},
                    {"REGION":"용인시","TM":"22","ALL_PASNG_ALL_CNT":99},
                    {"REGION":"수원시","TM":"23","ALL_PASNG_ALL_CNT":100}]}]}""");
        long riders = GgNightBusClient.parseNightRiders(body, "용인", 23);
        assertThat(riders).isEqualTo(47); // 용인 23시(30+17)만, 22시·타지역 제외
    }

    @Test void errorResultCodeThrows() throws Exception {
        JsonNode body = mapper.readTree("""
                {"SIGUNBUSANULUSERCNT":[{"head":[{"RESULT":{"CODE":"ERROR-300","MESSAGE":"인증키오류"}}]}]}""");
        assertThatThrownBy(() -> GgNightBusClient.parseNightRiders(body, "용인", 23))
                .isInstanceOf(PublicDataException.class);
    }
}
