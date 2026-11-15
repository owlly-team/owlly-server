package com.yongyong.owlly.domain.demand.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 키가 없을 때: 택시는 null(추정치), 심야 버스는 CSV 실측 스냅샷으로 대체되는지 검증. */
class PublicDemandStatsProviderTest {

    @Test void fallsBackToRealCsvSnapshotForNightBusWhenApiDisabled() {
        var provider = new PublicDemandStatsProvider(new YonginTaxiClient(""), new GgNightBusClient(""));
        var stats = provider.fetchFor(2025, 12);

        // 버스는 라이브가 막혀도 CSV 실측 스냅샷(용인 평일 심야 ≈ 14,438명)으로 채운다
        assertThat(stats.hasNightBus()).isTrue();
        assertThat(stats.nightBusRiders()).isEqualTo(PublicDemandStatsProvider.NIGHT_BUS_DAILY_SNAPSHOT);
        // 택시는 키가 없으면 비운다(추정치로 표시)
        assertThat(stats.hasTaxi()).isFalse();
    }
}
