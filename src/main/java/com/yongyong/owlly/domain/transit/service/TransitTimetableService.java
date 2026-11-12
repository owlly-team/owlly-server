package com.yongyong.owlly.domain.transit.service;

import com.yongyong.owlly.domain.transit.client.GbisBusClient;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayLastTimes;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayType;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.RouteLastTimes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 버스 노선의 막차 시각을 알려 준다. 홈을 열 때마다 불리는 API 라서 GBIS 응답을 서버 메모리에 잠시 둔다.
 *
 * <p>노선의 막차 시간표는 거의 바뀌지 않으므로 6시간 동안 같은 값을 쓴다. 서버를 다시 켜면 비워진다.
 */
@Service
public class TransitTimetableService {

    static final Duration CACHE_TTL = Duration.ofHours(6);

    private final GbisBusClient gbisBusClient;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public TransitTimetableService(GbisBusClient gbisBusClient) {
        this(gbisBusClient, Clock.systemUTC());
    }

    TransitTimetableService(GbisBusClient gbisBusClient, Clock clock) {
        this.gbisBusClient = gbisBusClient;
        this.clock = clock;
    }

    /**
     * 노선의 해당 요일·방향 막차 시각. 그 요일에 막차가 없으면 빈 값.
     *
     * @param fromTerminal true 면 종점에서 출발하는 막차, false 면 기점에서 출발하는 막차
     * @throws GbisBusClient.GbisException GBIS 를 쓸 수 없을 때
     */
    public Optional<LocalTime> lastDeparture(long routeId, DayType dayType, boolean fromTerminal) {
        Instant now = clock.instant();
        Cached cached = cache.get(routeId);
        if (cached == null || cached.fetchedAt().plus(CACHE_TTL).isBefore(now)) {
            cached = new Cached(gbisBusClient.findLastTimes(routeId), now);
            cache.put(routeId, cached);
        }
        DayLastTimes day = cached.times().byDay().get(dayType);
        if (day == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(fromTerminal ? day.downLast() : day.upLast());
    }

    private record Cached(RouteLastTimes times, Instant fetchedAt) {}
}
