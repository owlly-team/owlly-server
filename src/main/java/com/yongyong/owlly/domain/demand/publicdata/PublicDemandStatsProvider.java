package com.yongyong.owlly.domain.demand.publicdata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 수요 산출이 쓰는 공공데이터 실측을 한데 모은다. 키가 없거나 호출이 실패하면 그 항목만 null 로 두고(추정치로 대체),
 * 산출 파이프라인은 절대 막지 않는다(try/catch 로 모든 실패를 삼킨다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDemandStatsProvider {

    private static final String REGION = "용인";
    private static final int NIGHT_FROM_HOUR = 23;

    /**
     * 경기 시군별 버스 이용객수 CSV(경기데이터드림, 2025-12) 용인 평일 심야(23~01시) 대표 1일 전체통행 = 14,438명.
     * openapi.gg.go.kr 라이브 호출이 막히거나 키가 없을 때 쓰는 실측 스냅샷(지어낸 값 아님).
     */
    static final long NIGHT_BUS_DAILY_SNAPSHOT = 14_438;

    private final YonginTaxiClient taxiClient;
    private final GgNightBusClient busClient;

    /** 해당 연·월의 용인 실측 요약. 못 받은 항목은 null. */
    public PublicDemandStats fetchFor(int year, int month) {
        Long taxiRequest = null, taxiMatched = null, nightBus = null;
        try {
            if (taxiClient.enabled()) {
                var taxi = taxiClient.fetch(year, month);
                taxiRequest = taxi.requestCount();
                taxiMatched = taxi.matchedCount();
            }
        } catch (RuntimeException e) {
            log.warn("용인택시 공공데이터를 쓰지 못해 추정치로 대체합니다: {}", e.getMessage());
        }
        try {
            if (busClient.enabled()) {
                nightBus = busClient.fetchNightRiders(REGION, NIGHT_FROM_HOUR);
            }
        } catch (RuntimeException e) {
            log.warn("경기 버스 라이브 호출 실패 → CSV 실측 스냅샷으로 대체합니다: {}", e.getMessage());
        }
        // 라이브(openapi.gg.go.kr)가 막혀도 CSV 실측 스냅샷을 써서 "심야 버스 이용객"은 실데이터로 유지한다.
        if (nightBus == null) {
            nightBus = NIGHT_BUS_DAILY_SNAPSHOT;
        }
        return new PublicDemandStats(taxiRequest, taxiMatched, nightBus);
    }
}
