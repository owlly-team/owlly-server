package com.yongyong.owlly.domain.transit.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayType;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.GbisException;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.RouteLastTimes;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** GBIS 노선정보 응답을 막차 시각으로 바꾸는 규칙. 실제 응답 모양(문자열 시각, 0·null 섞임)으로 확인한다. */
class GbisBusClientTest {

    private static JsonNode json(String text) throws Exception {
        return new ObjectMapper().readTree(text);
    }

    @Test
    void 요일별_기점_종점_막차를_읽는다_없는_값은_null() throws Exception {
        JsonNode body = json("""
                {"response":{"msgHeader":{"resultCode":0,"resultMessage":"정상적으로 처리되었습니다."},
                 "msgBody":{"busRouteInfoItem":{"routeName":"720-3",
                   "upLastTime":"20:15","downLastTime":"23:00",
                   "satUpLastTime":"20:15","satDownLastTime":"23:00",
                   "sunUpLastTime":0,"sunDownLastTime":"23:00"}}}}
                """);

        RouteLastTimes times = GbisBusClient.parse(234000068L, body);

        assertThat(times.routeName()).isEqualTo("720-3");
        assertThat(times.byDay().get(DayType.WEEKDAY).downLast()).isEqualTo(LocalTime.of(23, 0));
        assertThat(times.byDay().get(DayType.WEEKDAY).upLast()).isEqualTo(LocalTime.of(20, 15));
        assertThat(times.byDay().get(DayType.SUNDAY).upLast()).as("숫자 0 은 없는 값").isNull();
        assertThat(times.byDay().get(DayType.HOLIDAY).downLast()).as("필드가 아예 없으면 null").isNull();
    }

    @Test
    void 한_자리_시각도_읽는다() throws Exception {
        JsonNode body = json("""
                {"response":{"msgHeader":{"resultCode":0},
                 "msgBody":{"busRouteInfoItem":{"routeName":"1101N","downLastTime":"1:00"}}}}
                """);

        assertThat(GbisBusClient.parse(1L, body).byDay().get(DayType.WEEKDAY).downLast()).isEqualTo(LocalTime.of(1, 0));
    }

    @Test
    void 오류_응답이거나_노선이_없으면_GbisException() throws Exception {
        JsonNode denied = json("{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"}}}");
        JsonNode empty = json("{\"response\":{\"msgHeader\":{\"resultCode\":0},\"msgBody\":{}}}");

        assertThatThrownBy(() -> GbisBusClient.parse(1L, denied)).isInstanceOf(GbisException.class);
        assertThatThrownBy(() -> GbisBusClient.parse(1L, empty)).isInstanceOf(GbisException.class);
        assertThatThrownBy(() -> GbisBusClient.parse(1L, null)).isInstanceOf(GbisException.class);
    }
}
