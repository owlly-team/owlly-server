package com.yongyong.owlly.domain.demand.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 회귀 엔진이 진짜로 데이터에서 계수를 복원하는지(실제 ML 수학 검증). */
class OlsLinearRegressionTest {

    @Test void recoversKnownCoefficientsFromCleanData() {
        // y = 5 + 2·x1 − 3·x2
        double[][] x = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 2}};
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = 5 + 2 * x[i][0] - 3 * x[i][1];
        }
        var model = new OlsLinearRegression(0.0);
        model.fit(x, y);
        double[] b = model.coefficients();
        assertThat(b[0]).isCloseTo(5, within(1e-6)); // 절편
        assertThat(b[1]).isCloseTo(2, within(1e-6)); // x1
        assertThat(b[2]).isCloseTo(-3, within(1e-6)); // x2
        assertThat(model.predict(new double[]{4, 2})).isCloseTo(5 + 8 - 6, within(1e-6));
    }

    @Test void ridgeKeepsSolvableWhenMoreFeaturesThanSamples() {
        // 표본 1개, 특성 2개 → 정규화 없으면 불능. 릿지로 안정적으로 풀려야 한다.
        double[][] x = {{2, 1}};
        double[] y = {10};
        var model = new OlsLinearRegression(1.0);
        model.fit(x, y);
        assertThat(model.coefficients()).isNotNull();
        assertThat(model.predict(new double[]{2, 1})).isFinite();
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
