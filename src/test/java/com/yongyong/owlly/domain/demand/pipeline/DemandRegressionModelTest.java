package com.yongyong.owlly.domain.demand.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.demand.pipeline.DemandAggregator.Observation;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** 수요 회귀 모델이 데이터에서 계수를 학습하고(하드코딩 아님), 관측치로 수요를 예측하는지 검증. */
class DemandRegressionModelTest {

    private final DemandRegressionModel model = new DemandRegressionModel();

    @Test void learnsCoefficientsFromTrainingData() {
        double[] c = model.coefficients(); // [절편, 귀가, 매칭실패]
        assertThat(c[0]).isCloseTo(0.0, Offset.offset(1e-6)); // 절편 ≈ 0
        assertThat(c[1]).isCloseTo(3.0, Offset.offset(1e-6)); // 귀가 계수 ≈ 3 (coverage)
        assertThat(c[2]).isCloseTo(0.5, Offset.offset(1e-6)); // 매칭실패 계수 ≈ 0.5
    }

    @Test void predictsWeeklyDemandFromObservations() {
        Observation o = new Observation();
        o.journeyCount = 47;
        o.failedJourneyCount = 17;
        // 3·47 + 0.5·17 = 149.5
        assertThat(model.predictWeeklyDemand(o)).isBetween(148L, 151L);
    }

    @Test void neverPredictsNegative() {
        assertThat(model.predictWeeklyDemand(new Observation())).isZero();
    }
}
