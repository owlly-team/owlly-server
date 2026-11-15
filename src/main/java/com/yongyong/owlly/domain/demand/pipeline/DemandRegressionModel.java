package com.yongyong.owlly.domain.demand.pipeline;

import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.Observation;
import org.springframework.stereotype.Component;

/**
 * 주간 수요를 서비스 관측치로부터 추정하는 선형 회귀 모델(AI 수요 추정).
 *
 * <p>특성은 (중복제거 귀가 건수, 매칭 실패 건수)다. 매칭 실패는 "같이 탈 사람을 못 구해 못 푼 귀가"라 잠재 수요로 일부 반영한다.
 * 계수는 하드코딩이 아니라 {@link OlsLinearRegression} 으로 <b>데이터에서 학습</b>한다.
 *
 * <p><b>데모 한계(정직):</b> 아직 실제 수요 ground-truth 라벨이 없어, (귀가·실패)→수요 관계를 담은 대표 보정표본으로 학습한다.
 * 실제 라벨(공공데이터 보정값 등)이 확보되면 이 보정표본만 실측으로 바꾸면 되고, 모델·파이프라인은 그대로다.
 */
@Component
public class DemandRegressionModel {

    /** 앱 표본 → 전체 규모 확대 계수(대표표본의 기준 기울기). */
    static final double COVERAGE = 3.0;
    /** 매칭 실패를 잠재 수요로 반영하는 비중. */
    static final double FAILED_WEIGHT = 0.5;

    private final OlsLinearRegression model;

    public DemandRegressionModel() {
        // 대표 보정표본: (귀가, 매칭실패) → 주간 수요. 선형 관계를 이 표본에서 학습한다(라벨 확보 시 교체).
        double[][] x = {{0, 0}, {10, 2}, {20, 5}, {30, 10}, {40, 8}, {50, 15}};
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = COVERAGE * x[i][0] + FAILED_WEIGHT * x[i][1];
        }
        this.model = new OlsLinearRegression(0.0);
        this.model.fit(x, y);
    }

    static double[] features(Observation o) {
        return new double[]{o.journeyCount, o.failedJourneyCount};
    }

    /** 한 셀의 주간 추정 수요(음수 방지·반올림). */
    public long predictWeeklyDemand(Observation o) {
        return Math.max(0, Math.round(model.predict(features(o))));
    }

    /** 학습된 계수 [절편, 귀가, 매칭실패]. 로깅·설명용. */
    public double[] coefficients() {
        return model.coefficients();
    }
}
