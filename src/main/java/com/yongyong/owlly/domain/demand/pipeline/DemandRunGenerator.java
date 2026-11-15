package com.yongyong.owlly.domain.demand.pipeline;

import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import com.yongyong.owlly.domain.demand.publicdata.PublicDemandStats;
import com.yongyong.owlly.domain.demand.publicdata.PublicDemandStatsProvider;
import com.yongyong.owlly.domain.demand.repository.DemandEstimateRepository;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수요 산출 오케스트레이터: 한 기간에 대해 run 을 만들고 집계→추정→저장까지 한 트랜잭션으로 수행한다.
 *
 * <p>DemandRun 을 RUNNING 으로 열고, 기간의 귀가·신청을 읽어 {@link DemandAggregator} 로 집계,
 * {@link DemandEstimator}(데모 공식) 로 추정치를 채워 demand_estimates 를 저장한 뒤 SUCCEEDED 로 전이한다.
 * 조회 API(API-43~45)는 이 결과를 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
public class DemandRunGenerator {

    private final DemandRunRepository demandRuns;
    private final DemandEstimateRepository demandEstimates;
    private final JourneyRepository journeys;
    private final RideRequestRepository rideRequests;
    private final DemandAggregator aggregator;
    private final DemandEstimator estimator;
    private final AdministrativeAreaRepository areas;
    private final PublicDemandStatsProvider publicStats;

    /** periodStart~periodEnd(양끝 포함) 의 서비스 기록으로 한 번의 산출을 만든다. */
    @Transactional
    public DemandRun generate(LocalDate periodStart, LocalDate periodEnd) {
        Instant start = periodStart.atStartOfDay(DemandAggregator.SERVICE_ZONE).toInstant();
        Instant end = periodEnd.plusDays(1).atStartOfDay(DemandAggregator.SERVICE_ZONE).toInstant();

        DemandRun run = demandRuns.save(DemandRun.builder()
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .modelVersion("demo-sim-v1")
                .inputSources("{\"service\":\"journeys,ride_requests\",\"public\":\"simulated\"}")
                .status(DemandRunStatus.RUNNING)
                .build());

        var cells = aggregator.aggregate(
                journeys.findByIntendedDepartureAtGreaterThanEqualAndIntendedDepartureAtLessThan(start, end),
                rideRequests.findByDepartureAtGreaterThanEqualAndDepartureAtLessThan(start, end));

        // 기존 공급(실제 심야 노선)을 도착동 이름으로 판정하기 위해 이름을 함께 넘긴다.
        var dongIds = cells.keySet().stream().map(DemandAggregator.CellKey::destinationDongId).distinct().toList();
        Map<Long, String> dongNames = areas.findAllById(dongIds).stream()
                .collect(Collectors.toMap(AdministrativeArea::getId, AdministrativeArea::getName));

        // 공공데이터 실측(용인 택시·심야 버스). 키 없음/실패면 항목별 null → 추정치로 표기.
        PublicDemandStats stats = publicStats.fetchFor(periodEnd.getYear(), periodEnd.getMonthValue());
        demandEstimates.saveAll(estimator.estimate(run.getId(), cells, dongNames, stats));

        run.markSucceeded(Instant.now()); // 영속 상태라 커밋 시 반영
        return run;
    }
}
