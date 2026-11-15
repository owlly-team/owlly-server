package com.yongyong.owlly.domain.demand.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.CellKey;
import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.Observation;
import com.yongyong.owlly.domain.demand.publicdata.PublicDemandStats;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 데모 추정 공식 단위 검증: 추정 수요 확대, 후보 기준·순위, 표본 미달 숨김, 기존 공급 시뮬, 신규 필드 전달. */
class DemandEstimatorTest {

    private final DemandEstimator estimator = new DemandEstimator(new ObjectMapper(), new DemandRegressionModel());

    private Observation obs(int journeys, int registered, int failed, int solo) {
        Observation o = new Observation();
        o.journeyCount = journeys;
        o.pooledRegisteredCount = registered;
        o.failedJourneyCount = failed;
        o.soloSelectedCount = solo;
        return o;
    }

    private CellKey cell(long dong) {
        return new CellKey(5L, 10L, dong, 4, LocalTime.of(23, 30));
    }

    @Test void scalesWeeklyDemandRanksCandidatesAndSuppressesSmallSamples() {
        Map<CellKey, Observation> cells = new LinkedHashMap<>();
        CellKey big = cell(100L);    // journeyCount 30 → 주 90, 후보
        CellKey mid = cell(101L);    // journeyCount 20 → 주 60, 후보
        CellKey small = cell(102L);  // journeyCount 3  → 표본 미달(숨김)
        cells.put(big, obs(30, 86, 31, 25));
        cells.put(mid, obs(20, 40, 10, 8));
        cells.put(small, obs(3, 2, 1, 1));
        // 도착동 이름(기존 공급 실제 노선 판정용)
        Map<Long, String> dongNames = Map.of(100L, "처인구", 101L, "수지구", 102L, "영통구");

        Map<Long, DemandEstimate> byDong = estimator.estimate(1L, cells, dongNames, null).stream()
                .collect(Collectors.toMap(DemandEstimate::getDestinationDongId, Function.identity()));

        // 추정 수요 = 회귀 모델 예측(3·귀가 + 0.5·매칭실패). 예측값이라 범위로 검증.
        assertThat(byDong.get(100L).getEstimatedWeeklyDemand().intValue()).isBetween(100, 110); // 30·3+31·0.5≈106
        assertThat(byDong.get(101L).getEstimatedWeeklyDemand().intValue()).isBetween(62, 68);   // 20·3+10·0.5=65
        assertThat(byDong.get(102L).getEstimatedWeeklyDemand().intValue()).isBetween(8, 12);    // 3·3+1·0.5≈10

        // 표본 미달(5건 미만)은 숨김, 나머지는 노출
        assertThat(byDong.get(102L).isSuppressed()).isTrue();
        assertThat(byDong.get(100L).isSuppressed()).isFalse();

        // 후보 기준(주 50회 이상) 넘는 big·mid 만 순위 1·2, small 은 순위 없음
        assertThat(byDong.get(100L).getCandidateRank()).isNotNull();
        assertThat(byDong.get(101L).getCandidateRank()).isNotNull();
        assertThat(byDong.get(102L).getCandidateRank()).isNull();
        assertThat(byDong.get(100L).getCandidateRank()).isNotEqualTo(byDong.get(101L).getCandidateRank());
        assertThat(byDong.get(100L).getCandidateThreshold()).isEqualByComparingTo("50");

        // 신규 필드: 동승 등록 그대로 전달, 기존 공급은 실제 심야 노선 기반(동 이름 매칭)
        assertThat(byDong.get(100L).getPooledRegisteredCount()).isEqualTo(86);
        assertThat(byDong.get(100L).getExistingSupply()).isEqualTo("N1 하루 1회");     // 처인구: N1 심야 1회
        assertThat(byDong.get(101L).getExistingSupply()).isEqualTo("1101N 평일 2회");  // 수지구: 1101N 평일 2회
        assertThat(byDong.get(102L).getExistingSupply()).isEqualTo("없음");            // 영통구: 심야 노선 없음

        // 근거 지표가 JSON 으로 채워짐(서비스 집계 실측 + 공공데이터 실제 출처 명시, 값은 라이브 연동 전 추정)
        assertThat(byDong.get(100L).getEvidence())
                .contains("동승 등록 86건", "매칭 실패 31건", "경기 시군별 버스 이용객수", "라이브 연동 전");
    }

    @Test void belowCandidateThresholdIsVisibleButNotRanked() {
        Map<CellKey, Observation> cells = new LinkedHashMap<>();
        CellKey low = cell(100L); // journeyCount 10 → 주 30 (<50) : 표본은 충분하나 후보는 아님
        cells.put(low, obs(10, 12, 3, 4));
        DemandEstimate e = estimator.estimate(1L, cells, Map.of(100L, "영통구"), null).get(0);
        assertThat(e.isSuppressed()).isFalse();
        assertThat(e.getCandidateRank()).isNull();
    }

    @Test void emptyCellsYieldNoEstimates() {
        assertThat(estimator.estimate(1L, Map.of(), Map.of(), null)).isEmpty();
    }

    @Test void usesRealPublicStatsInEvidenceWhenProvided() {
        Map<CellKey, Observation> cells = new LinkedHashMap<>();
        cells.put(cell(100L), obs(30, 86, 31, 25));
        // 택시 호출 1000·탑승 700(미충족 30%), 심야 버스 47명
        var stats = new PublicDemandStats(1000L, 700L, 47L);
        DemandEstimate e = estimator.estimate(1L, cells, Map.of(100L, "처인구"), stats).get(0);
        assertThat(e.getEvidence())
                .contains("용인 택시 호출 1000건·탑승 700건", "미충족 30%", "심야 버스 이용객 47명");
    }
}
