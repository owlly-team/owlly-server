package com.yongyong.owlly.domain.demand.pipeline;

import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 수요 산출 1단계: 서비스 귀가 기록·동승 신청을 (대학×거점×도착동×요일×30분 슬롯) 셀로 집계한다(관측값).
 *
 * <p>요일·슬롯은 서비스 현지 시간대(Asia/Seoul) 기준으로 끊는다. 취소된 귀가는 세지 않는다.
 * journeyCount·탑승/막차/혼자는 journeys 의 결과로, 동승 등록·매칭 실패는 ride_requests 로 센다.
 * (실패율 = failedJourneyCount / pooledRegisteredCount 의 분자·분모가 여기서 나온다.)
 */
@Component
public class DemandAggregator {

    /** 집계 경계(요일·슬롯)를 끊는 서비스 현지 시간대. */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 집계 단위 셀. weekday 는 0=일요일. slotStart 는 30분 슬롯 시작(현지). */
    public record CellKey(Long universityId, Long pickupPointId, Long destinationDongId, int weekday, LocalTime slotStart) {}

    /** 한 셀의 관측 집계값(가변 누적기). */
    public static final class Observation {
        public int journeyCount;
        public int failedJourneyCount;
        public int pooledJourneyCount;
        public int pooledRegisteredCount;
        public int busSelectedCount;
        public int soloSelectedCount;
    }

    /** 기간 안에서 읽은 귀가·신청을 셀별로 집계한다. 삽입 순서를 보존한다. */
    public Map<CellKey, Observation> aggregate(List<Journey> journeys, List<RideRequest> requests) {
        Map<CellKey, Observation> cells = new LinkedHashMap<>();
        for (Journey j : journeys) {
            if (j.getOutcome() == JourneyOutcome.CANCELLED) {
                continue;
            }
            Observation o = cells.computeIfAbsent(
                    cellOf(j.getUniversityId(), j.getPickupPointId(), j.getDestinationDongId(), j.getIntendedDepartureAt()),
                    k -> new Observation());
            o.journeyCount++;
            switch (j.getOutcome()) {
                case POOL_BOARDED -> o.pooledJourneyCount++;
                case BUS_SELECTED -> o.busSelectedCount++;
                case SOLO_SELECTED -> o.soloSelectedCount++;
                default -> { } // UNRESOLVED: 귀가 건수에만 반영
            }
        }
        for (RideRequest r : requests) {
            Observation o = cells.computeIfAbsent(
                    cellOf(r.getUniversityId(), r.getPickupPointId(), r.getDestinationDongId(), r.getDepartureAt()),
                    k -> new Observation());
            o.pooledRegisteredCount++;
            if (r.getStatus() == RideRequestStatus.FAILED) {
                o.failedJourneyCount++;
            }
        }
        return cells;
    }

    private CellKey cellOf(Long universityId, Long pickupPointId, Long destinationDongId, Instant at) {
        ZonedDateTime z = at.atZone(SERVICE_ZONE);
        int weekday = z.getDayOfWeek().getValue() % 7; // Mon=1..Sun=7 → Sun=0..Sat=6 (0=일요일)
        LocalTime slot = LocalTime.of(z.getHour(), z.getMinute() < 30 ? 0 : 30);
        return new CellKey(universityId, pickupPointId, destinationDongId, weekday, slot);
    }
}
