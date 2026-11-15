package com.yongyong.owlly.domain.demand.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.CellKey;
import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.Observation;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 집계 로직 단위 검증(DB 없이): 셀 버킷(요일·30분 슬롯), outcome별 카운트, 동승 등록·실패, 취소 제외. */
class DemandAggregatorTest {

    private final DemandAggregator aggregator = new DemandAggregator();

    // 2024-01-07 은 일요일 → weekday 0.
    private Instant at(int hour, int minute) {
        return ZonedDateTime.of(2024, 1, 7, hour, minute, 0, 0, DemandAggregator.SERVICE_ZONE).toInstant();
    }

    private Journey journey(Long dong, Instant at, JourneyOutcome outcome) {
        return Journey.builder().clientJourneyKey(UUID.randomUUID()).userId(1L).universityId(5L)
                .pickupPointId(10L).destinationDongId(dong).intendedDepartureAt(at).outcome(outcome).build();
    }

    private RideRequest request(Long dong, Instant at, RideRequestStatus status) {
        return RideRequest.builder().clientRequestKey(UUID.randomUUID()).journeyId(1L).universityId(5L)
                .pickupPointId(10L).districtId(2L).destinationDongId(dong).departureAt(at)
                .status(status).build();
    }

    @Test void bucketsByWeekdayAndHalfHourSlotAndCountsOutcomes() {
        var cells = aggregator.aggregate(List.of(
                journey(100L, at(23, 40), JourneyOutcome.POOL_BOARDED),
                journey(100L, at(23, 50), JourneyOutcome.BUS_SELECTED),   // 같은 23:30 슬롯
                journey(100L, at(23, 10), JourneyOutcome.SOLO_SELECTED),  // 다른 23:00 슬롯
                journey(100L, at(23, 45), JourneyOutcome.CANCELLED)),     // 취소 → 제외
                List.of());

        CellKey slot2330 = new CellKey(5L, 10L, 100L, 0, LocalTime.of(23, 30));
        CellKey slot2300 = new CellKey(5L, 10L, 100L, 0, LocalTime.of(23, 0));
        assertThat(cells).containsOnlyKeys(slot2330, slot2300);

        Observation a = cells.get(slot2330);
        assertThat(a.journeyCount).isEqualTo(2); // 취소 제외, POOL+BUS 만
        assertThat(a.pooledJourneyCount).isEqualTo(1);
        assertThat(a.busSelectedCount).isEqualTo(1);
        assertThat(a.soloSelectedCount).isZero();
        assertThat(cells.get(slot2300).soloSelectedCount).isEqualTo(1);
    }

    @Test void countsRegisteredAndFailedFromRideRequests() {
        var cells = aggregator.aggregate(List.of(),
                List.of(request(100L, at(23, 40), RideRequestStatus.MATCHED),
                        request(100L, at(23, 35), RideRequestStatus.FAILED),
                        request(100L, at(23, 31), RideRequestStatus.WAITING)));

        Observation o = cells.get(new CellKey(5L, 10L, 100L, 0, LocalTime.of(23, 30)));
        assertThat(o.pooledRegisteredCount).isEqualTo(3); // 동승 등록(실패율 분모)
        assertThat(o.failedJourneyCount).isEqualTo(1);    // 매칭 실패(실패율 분자)
    }

    @Test void mergesJourneyAndRequestCountsIntoSameCell() {
        var cells = aggregator.aggregate(
                List.of(journey(100L, at(23, 40), JourneyOutcome.POOL_BOARDED)),
                List.of(request(100L, at(23, 45), RideRequestStatus.FAILED)));
        Observation o = cells.get(new CellKey(5L, 10L, 100L, 0, LocalTime.of(23, 30)));
        assertThat(o.journeyCount).isEqualTo(1);
        assertThat(o.pooledJourneyCount).isEqualTo(1);
        assertThat(o.pooledRegisteredCount).isEqualTo(1);
        assertThat(o.failedJourneyCount).isEqualTo(1);
    }

    @Test void emptyInputsYieldNoCells() {
        assertThat(aggregator.aggregate(List.of(), List.of())).isEmpty();
    }
}
