package com.yongyong.owlly.domain.demand.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.demand.dto.DemandCandidate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.service.DemandQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 데모 시더 검증: 단국대 세그먼트가 Figma 볼륨대로 산출되고, 신규 필드가 차며, 멱등하게 한 번만 심긴다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false"})
class DemandSimulationSeederIntegrationTest {

    @Autowired DemandSimulationSeeder seeder;
    @Autowired DemandQueryService demandQueryService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void cleanup() {
        jdbc.update("delete from ride_requests where destination_dong_id in (select id from administrative_areas where official_code like 'DEMO-%')");
        jdbc.update("delete from journeys where destination_dong_id in (select id from administrative_areas where official_code like 'DEMO-%')");
        jdbc.update("delete from demand_estimates where run_id in (select id from demand_runs where model_version='demo-sim-v1')");
        jdbc.update("delete from demand_runs where model_version='demo-sim-v1'");
        jdbc.update("delete from pickup_points where area_id in (select id from administrative_areas where official_code like 'DEMO-%')");
        jdbc.update("delete from administrative_areas where official_code like 'DEMO-%'");
    }

    @Test void seedsFigmaSegmentsWithNewFieldsAndIsIdempotent() {
        DemandRun run = seeder.seedAndGenerateOnce().orElseThrow();

        List<DemandCandidate> list = demandQueryService.getCandidates(
                new DemandQueryService.Query(run.getId(), null, null, null, null, false, null, 100)).data();

        // 후보 4개(정문→처인·죽전→처인·정문→영통·강남→기흥) 노출, 수지구(3건)는 표본 미달로 빠짐
        assertThat(list).hasSize(4);
        assertThat(list).noneMatch(c -> "수지구".equals(c.destinationDongName()));
        assertThat(list).allMatch(c -> c.candidateRank() != null); // 넷 다 주50회 이상이라 후보

        DemandCandidate frontToCheoin = list.stream()
                .filter(c -> "단국대 정문".equals(c.pickupPointName()) && "처인구".equals(c.destinationDongName()))
                .findFirst().orElseThrow();
        assertThat(frontToCheoin.estimatedWeeklyDemand().intValue()).isBetween(143, 158); // 회귀 예측 47·3+17·0.5≈150
        assertThat(frontToCheoin.pooledRegisteredCount()).isEqualTo(47);               // 동승 등록(실패율 분모)
        assertThat(frontToCheoin.failedJourneyCount()).isEqualTo(17);                   // 매칭 실패
        assertThat(frontToCheoin.existingSupply()).isEqualTo("N1 하루 1회");             // 처인구: 실제 N1 심야 노선

        // 멱등: 두 번째 호출은 이미 있으므로 새로 심지 않는다
        Optional<DemandRun> second = seeder.seedAndGenerateOnce();
        assertThat(second).isEmpty();
    }
}
