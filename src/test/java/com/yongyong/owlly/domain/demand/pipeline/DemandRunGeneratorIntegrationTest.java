package com.yongyong.owlly.domain.demand.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.demand.dto.DemandCandidate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
import com.yongyong.owlly.domain.demand.service.DemandQueryService;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 수요 산출 파이프라인 end-to-end: 서비스 귀가·동승 신청을 시드 → generate() → 조회 API(DemandQueryService)로
 * 그대로 읽히는지 검증한다. 집계 counts·추정 수요·후보 순위·표본 미달 숨김·신규 필드까지 한 번에 확인.
 */
@SpringBootTest(properties = {"owlly.matching.enabled=false"})
class DemandRunGeneratorIntegrationTest {

    @Autowired DemandRunGenerator generator;
    @Autowired DemandQueryService demandQueryService;
    @Autowired DemandRunRepository demandRuns;
    @Autowired JourneyRepository journeys;
    @Autowired RideRequestRepository rideRequests;
    @Autowired PickupPointRepository pickups;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired UniversityRepository universities;
    @Autowired JdbcTemplate jdbc;

    Long university;
    AdministrativeArea city, dongBig, dongSmall;
    PickupPoint pickup;
    final LocalDate periodStart = LocalDate.of(2099, 1, 5), periodEnd = LocalDate.of(2099, 1, 11);
    final List<Long> journeyIds = new ArrayList<>(), rideIds = new ArrayList<>(), areaIds = new ArrayList<>();
    Long generatedRunId;

    @BeforeEach void setup() {
        university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        city = area("산출시", AreaLevel.CITY, null);
        dongBig = area("많은동", AreaLevel.DONG, city.getId());
        dongSmall = area("적은동", AreaLevel.DONG, city.getId());
        pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(university).areaId(city.getId())
                .name("산출정문").meetingNote("정문").latitude(new BigDecimal("37.2")).longitude(new BigDecimal("127.05"))
                .active(true).build());

        // 많은동: 귀가 20건(→ 주 60, 후보) + 동승 신청 20건(실패 6) / 전부 2099-01-06(기간 안) 23:40 KST
        for (int i = 0; i < 20; i++) {
            journey(dongBig, JourneyOutcome.POOL_BOARDED);
            request(dongBig, i < 6 ? RideRequestStatus.FAILED : RideRequestStatus.MATCHED);
        }
        // 적은동: 귀가 3건 → 표본 미달(숨김)
        for (int i = 0; i < 3; i++) {
            journey(dongSmall, JourneyOutcome.POOL_BOARDED);
        }
    }

    @AfterEach void cleanup() {
        if (generatedRunId != null) {
            jdbc.update("delete from demand_estimates where run_id=?", generatedRunId);
            jdbc.update("delete from demand_runs where id=?", generatedRunId);
        }
        rideIds.forEach(id -> jdbc.update("delete from ride_requests where id=?", id));
        journeyIds.forEach(id -> jdbc.update("delete from journeys where id=?", id));
        pickups.deleteById(pickup.getId());
        areaIds.forEach(id -> jdbc.update("delete from administrative_areas where id=?", id));
    }

    @Test void generatesReadableRunWithAggregatesEstimatesRankAndNewFields() {
        DemandRun run = generator.generate(periodStart, periodEnd);
        generatedRunId = run.getId();

        // run 은 조회 가능한 성공 상태
        assertThat(demandRuns.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(DemandRunStatus.SUCCEEDED);

        List<DemandCandidate> list = demandQueryService.getCandidates(
                new DemandQueryService.Query(run.getId(), null, null, null, null, false, null, 100)).data();

        // 많은동 셀은 노출, 적은동(표본 미달)은 빠짐
        DemandCandidate big = list.stream()
                .filter(c -> c.destinationDongId().equals(String.valueOf(dongBig.getId()))).findFirst().orElseThrow();
        assertThat(list).noneMatch(c -> c.destinationDongId().equals(String.valueOf(dongSmall.getId())));

        // 집계 counts
        assertThat(big.journeyCount()).isEqualTo(20);
        assertThat(big.pooledJourneyCount()).isEqualTo(20);
        assertThat(big.pooledRegisteredCount()).isEqualTo(20); // 동승 등록(실패율 분모)
        assertThat(big.failedJourneyCount()).isEqualTo(6);     // 매칭 실패(실패율 분자)

        // 추정·후보: 회귀 예측(20·3+6·0.5≈63) ≥ 50 → 후보(유일하므로 1위)
        assertThat(big.estimatedWeeklyDemand().intValue()).isBetween(55, 70);
        assertThat(big.candidateRank()).isEqualTo(1);

        // 신규 필드: "많은동"은 실제 심야 노선이 없으므로 "없음"
        assertThat(big.existingSupply()).isEqualTo("없음");
        assertThat(big.evidence()).isNotEmpty();
    }

    // --- helpers ---

    private Instant inPeriod() {
        return ZonedDateTime.of(2099, 1, 6, 23, 40, 0, 0, DemandAggregator.SERVICE_ZONE).toInstant();
    }

    private void journey(AdministrativeArea dong, JourneyOutcome outcome) {
        Journey j = journeys.saveAndFlush(Journey.builder().clientJourneyKey(UUID.randomUUID())
                .userId(1L).universityId(university).pickupPointId(pickup.getId())
                .destinationDongId(dong.getId()).intendedDepartureAt(inPeriod()).outcome(outcome).build());
        journeyIds.add(j.getId());
    }

    private void request(AdministrativeArea dong, RideRequestStatus status) {
        RideRequest r = rideRequests.saveAndFlush(RideRequest.builder().clientRequestKey(UUID.randomUUID())
                .journeyId(1L).universityId(university).pickupPointId(pickup.getId()).districtId(city.getId())
                .destinationDongId(dong.getId()).departureAt(inPeriod())
                .dropoffLatEncrypted("x").dropoffLngEncrypted("x")
                .sameGenderOnly(true).genderSnapshot(com.yongyong.owlly.domain.user.entity.Gender.FEMALE)
                .status(status).build());
        rideIds.add(r.getId());
    }

    private AdministrativeArea area(String name, AreaLevel level, Long parent) {
        AdministrativeArea a = areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode("DG" + UUID.randomUUID().toString().substring(0, 8))
                .name(name).level(level).parentId(parent).build());
        areaIds.add(a.getId());
        return a;
    }
}
