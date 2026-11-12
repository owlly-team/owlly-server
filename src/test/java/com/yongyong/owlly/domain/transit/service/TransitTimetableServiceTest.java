package com.yongyong.owlly.domain.transit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.domain.transit.client.GbisBusClient;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayLastTimes;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayType;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.RouteLastTimes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 막차 시각 캐시: 6시간 동안은 GBIS 를 다시 부르지 않는다. */
class TransitTimetableServiceTest {

    private final GbisBusClient client = mock(GbisBusClient.class);

    private void stub() {
        when(client.findLastTimes(7L)).thenReturn(new RouteLastTimes(7L, "720-3", Map.of(
                DayType.WEEKDAY, new DayLastTimes(LocalTime.of(20, 15), LocalTime.of(23, 0)),
                DayType.SUNDAY, new DayLastTimes(null, null))));
    }

    @Test
    void 방향과_요일에_맞는_막차를_돌려주고_없는_요일은_빈_값() {
        stub();
        TransitTimetableService service = new TransitTimetableService(client, Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC));

        assertThat(service.lastDeparture(7L, DayType.WEEKDAY, true)).contains(LocalTime.of(23, 0));
        assertThat(service.lastDeparture(7L, DayType.WEEKDAY, false)).contains(LocalTime.of(20, 15));
        assertThat(service.lastDeparture(7L, DayType.SUNDAY, true)).isEmpty();
        assertThat(service.lastDeparture(7L, DayType.HOLIDAY, true)).as("그 요일 구분 자체가 없음").isEmpty();
    }

    @Test
    void 같은_노선은_6시간_안에는_한_번만_부르고_지나면_다시_부른다() {
        stub();
        Instant start = Instant.parse("2026-11-05T10:00:00Z");
        Clock[] clock = {Clock.fixed(start, ZoneOffset.UTC)};
        TransitTimetableService service = new TransitTimetableService(client, new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return clock[0].instant(); }
        });

        service.lastDeparture(7L, DayType.WEEKDAY, true);
        clock[0] = Clock.fixed(start.plusSeconds(5 * 3600), ZoneOffset.UTC);
        service.lastDeparture(7L, DayType.WEEKDAY, true);
        verify(client, times(1)).findLastTimes(7L);

        clock[0] = Clock.fixed(start.plusSeconds(7 * 3600), ZoneOffset.UTC);
        service.lastDeparture(7L, DayType.WEEKDAY, true);
        verify(client, times(2)).findLastTimes(7L);
    }
}
