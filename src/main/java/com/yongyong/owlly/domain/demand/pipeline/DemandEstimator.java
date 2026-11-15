package com.yongyong.owlly.domain.demand.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.CellKey;
import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.Observation;
import com.yongyong.owlly.domain.demand.publicdata.PublicDemandStats;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 수요 산출 2단계: 집계된 셀을 받아 추정 수요·후보 순위·기존 공급·근거를 채운다.
 *
 * <p><b>데모용 단순 공식이다.</b> 실제 모델·가중치·후보 기준은 AI 파트에서 확정 예정이며, 확정되면 이 클래스만 교체한다.
 * 공공데이터(생활이동·버스·택시·심야노선)는 아직 미확보라, 보정과 근거 지표는 시뮬레이션 값으로 채운다.
 *
 * <ul>
 *   <li>추정 주간 수요 = 관측 귀가 × 커버리지 계수(앱 사용자가 전체의 일부라는 가정으로 확대)
 *   <li>총점(정렬용) = 가중합(정규화 추정수요·공급 부족·매칭 실패율) — 화면에 총점 자체는 안 보인다
 *   <li>후보 순위 = 표본 충분 + 추정수요 ≥ 후보 기준인 셀을 총점 내림차순으로 1..N
 *   <li>표본 미달(5건 미만)은 suppressed=true 로 숨김
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DemandEstimator {

    /** 최소 표본(미만이면 숨김). */
    static final int MIN_SAMPLE = 5;
    /** 후보로 보는 최소 주간 추정 수요(데모 기준). */
    static final int CANDIDATE_MIN_WEEKLY = 50;
    /** 총점 가중치(데모): 추정수요·공급부족·실패율. */
    static final double W_DEMAND = 0.5, W_SUPPLY_GAP = 0.3, W_FAILURE = 0.2;

    private final ObjectMapper objectMapper;
    private final DemandRegressionModel regressionModel;

    /** 한 셀의 중간 계산 결과(정렬·순위 부여 전). */
    private record Scored(CellKey key, Observation obs, BigDecimal weeklyDemand,
                          String existingSupply, double score, boolean suppressed) {}

    /**
     * 집계 셀을 DemandEstimate 로 변환한다(순위는 총점 내림차순으로 부여).
     *
     * @param dongNames   도착동 id→이름. 기존 공급(실제 심야 노선)을 동 이름으로 판정하는 데 쓴다.
     * @param publicStats 공공데이터 실측 요약(용인 택시·심야 버스). 못 받은 항목은 추정치로 근거에 적는다.
     */
    public List<DemandEstimate> estimate(Long runId, Map<CellKey, Observation> cells,
                                         Map<Long, String> dongNames, PublicDemandStats publicStats) {
        if (cells.isEmpty()) {
            return List.of();
        }
        PublicDemandStats stats = publicStats == null ? PublicDemandStats.empty() : publicStats;
        // 추정 수요는 선형 회귀 모델이 (귀가·매칭실패)로부터 예측한다(하드코딩 배수 아님).
        long maxWeekly = cells.values().stream()
                .mapToLong(regressionModel::predictWeeklyDemand)
                .max().orElse(1);

        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<CellKey, Observation> e : cells.entrySet()) {
            Observation o = e.getValue();
            long weekly = regressionModel.predictWeeklyDemand(o);
            String existingSupply = realExistingSupply(dongNames.get(e.getKey().destinationDongId()));
            double normDemand = maxWeekly == 0 ? 0 : (double) weekly / maxWeekly;
            double failureRate = o.pooledRegisteredCount == 0 ? 0 : (double) o.failedJourneyCount / o.pooledRegisteredCount;
            double supplyGap = supplyGapOf(existingSupply);
            double score = W_DEMAND * normDemand + W_SUPPLY_GAP * supplyGap + W_FAILURE * failureRate;
            boolean suppressed = o.journeyCount < MIN_SAMPLE;
            scored.add(new Scored(e.getKey(), o, BigDecimal.valueOf(weekly), existingSupply, score, suppressed));
        }

        // 후보 순위: 표본 충분 + 추정수요 ≥ 기준 인 셀만 총점 내림차순으로 1..N.
        List<Scored> rankable = scored.stream()
                .filter(s -> !s.suppressed() && s.weeklyDemand().intValue() >= CANDIDATE_MIN_WEEKLY)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();
        Map<CellKey, Integer> rankByCell = new java.util.HashMap<>();
        for (int i = 0; i < rankable.size(); i++) {
            rankByCell.put(rankable.get(i).key(), i + 1);
        }

        Instant now = Instant.now();
        List<DemandEstimate> result = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            Integer rank = rankByCell.get(s.key());
            result.add(DemandEstimate.builder()
                    .runId(runId)
                    .universityId(s.key().universityId())
                    .pickupPointId(s.key().pickupPointId())
                    .destinationDongId(s.key().destinationDongId())
                    .weekday(s.key().weekday())
                    .slotStart(s.key().slotStart())
                    .journeyCount(s.obs().journeyCount)
                    .failedJourneyCount(s.obs().failedJourneyCount)
                    .pooledJourneyCount(s.obs().pooledJourneyCount)
                    .pooledRegisteredCount(s.obs().pooledRegisteredCount)
                    .busSelectedCount(s.obs().busSelectedCount)
                    .soloSelectedCount(s.obs().soloSelectedCount)
                    .estimatedWeeklyDemand(s.weeklyDemand())
                    .candidateThreshold(BigDecimal.valueOf(CANDIDATE_MIN_WEEKLY))
                    .candidateRank(rank)
                    .existingSupply(s.existingSupply())
                    .evidence(buildEvidence(s.obs(), now, stats))
                    .suppressed(s.suppressed())
                    .build());
        }
        return result;
    }

    /**
     * 기존 공급: 실제 용인 심야 버스 노선 기반(웹 확인).
     * N1 은 용인터미널~처인구청~구성역 심야 1일 1회(처인구 방면), 1101N 은 단국대(죽전·수지)~강남역 평일 심야 2회.
     * 데모 세그먼트의 도착동 이름으로 매칭하고, 해당 노선이 없으면 "없음".
     */
    private String realExistingSupply(String dongName) {
        if (dongName == null) {
            return "없음";
        }
        if (dongName.contains("처인")) {
            return "N1 하루 1회";
        }
        if (dongName.contains("수지") || dongName.contains("죽전")) {
            return "1101N 평일 2회";
        }
        return "없음";
    }

    /** 기존 공급이 적을수록 공급 부족(=노선 근거)이 크다. */
    private double supplyGapOf(String existingSupply) {
        return switch (existingSupply) {
            case "없음" -> 1.0;
            case "N1 하루 1회" -> 0.5;
            default -> 0.3; // 1101N 평일 2회 등
        };
    }

    /**
     * 근거 지표: 서비스 집계 3개는 관측값(실측). 심야 버스·택시는 공공데이터를 받았으면 실측,
     * 못 받았으면 추정치로 표기한다. 생활이동은 파일 기반이라 아직 추정치.
     */
    private String buildEvidence(Observation o, Instant now, PublicDemandStats stats) {
        List<Map<String, Object>> evidence = new java.util.ArrayList<>();
        evidence.add(serviceMetric("동승 등록", o.pooledRegisteredCount, "건", now));
        evidence.add(serviceMetric("매칭 실패", o.failedJourneyCount, "건", now));
        evidence.add(serviceMetric("혼자 택시 선택", o.soloSelectedCount, "건", now));

        if (stats.hasNightBus()) {
            evidence.add(realMetric("심야 버스 이용객", stats.nightBusRiders() + "명",
                    "경기 시군별 버스 이용객수 (경기데이터드림, 용인 평일 심야 23~01시)", now));
        } else {
            evidence.add(simulatedMetric("심야 버스 이용객", "경기 시군별 버스 이용객수 (경기데이터드림 SIGUNBUSANULUSERCNT)", now));
        }

        evidence.add(simulatedMetric("생활이동", "수도권 생활이동 (서울 열린데이터광장 OA-22300, 서울시·KT)", now));

        if (stats.hasTaxi()) {
            Double unmet = stats.taxiUnmetRate();
            String desc = "용인 택시 호출 " + stats.taxiRequestCount() + "건·탑승 " + stats.taxiMatchedCount() + "건"
                    + (unmet == null ? "" : " (미충족 " + Math.round(unmet * 100) + "%)");
            evidence.add(realMetric("심야 택시 운행", desc, "용인앱택시 운행현황 (data.go.kr 15125676)", now));
        } else {
            evidence.add(simulatedMetric("심야 택시 운행", "용인앱택시 운행현황 (data.go.kr 15125676)", now));
        }

        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            throw new OwllyException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 공공데이터 실측 근거(OBSERVED). description 에 실제 수치를 그대로 담는다. */
    private Map<String, Object> realMetric(String label, String valueText, String source, Instant now) {
        return Map.of("sourceLabel", source, "kind", "OBSERVED",
                "description", label + " " + valueText, "unit", "건", "asOf", now.toString());
    }

    private Map<String, Object> serviceMetric(String label, int value, String unit, Instant now) {
        return Map.of("sourceLabel", "서비스 귀가 집계", "kind", "OBSERVED",
                "description", label + " " + value + unit, "unit", unit, "asOf", now.toString());
    }

    private Map<String, Object> simulatedMetric(String label, String source, Instant now) {
        // 출처는 실제 공공데이터셋. 값은 라이브 연동(인증키) 전까지 추정치다.
        return Map.of("sourceLabel", source, "kind", "ESTIMATED",
                "description", label + " (추정치 · 라이브 연동 전)", "unit", "건", "asOf", now.toString());
    }
}
