package com.yongyong.owlly.domain.demand.pipeline;

/**
 * 선형 회귀(최소제곱, 릿지 정규화 지원)를 정규방정식으로 직접 푼다. 외부 ML 라이브러리 없이 순수 Java.
 *
 * <p>절편 포함 설계행렬 Z=[1,x] 에 대해 β = (ZᵀZ + λI)⁻¹ Zᵀy 를 가우스 소거로 구한다(절편은 정규화 제외).
 * 표본이 특성 수보다 적거나 특성이 공선적일 때도 λ(ridge)로 안정적으로 푼다.
 */
public class OlsLinearRegression {

    private final double ridge;
    private double[] beta; // [절편, 계수1..p]

    public OlsLinearRegression(double ridge) {
        this.ridge = ridge;
    }

    /** 특성행렬 x(n×p)와 목표 y(n)로 계수를 학습한다. */
    public void fit(double[][] x, double[] y) {
        int n = x.length;
        if (n == 0) {
            throw new IllegalArgumentException("학습 표본이 없습니다.");
        }
        int p = x[0].length;
        int d = p + 1; // 절편 포함

        double[][] z = new double[n][d];
        for (int i = 0; i < n; i++) {
            z[i][0] = 1.0;
            System.arraycopy(x[i], 0, z[i], 1, p);
        }

        // A = ZᵀZ + λI(절편 제외), b = Zᵀy
        double[][] a = new double[d][d];
        double[] b = new double[d];
        for (int r = 0; r < d; r++) {
            for (int c = 0; c < d; c++) {
                double s = 0;
                for (int i = 0; i < n; i++) {
                    s += z[i][r] * z[i][c];
                }
                a[r][c] = s;
            }
            double s = 0;
            for (int i = 0; i < n; i++) {
                s += z[i][r] * y[i];
            }
            b[r] = s;
        }
        for (int j = 1; j < d; j++) {
            a[j][j] += ridge;
        }
        this.beta = solve(a, b);
    }

    /** 학습된 계수로 한 특성벡터의 예측값. */
    public double predict(double[] features) {
        double v = beta[0];
        for (int j = 0; j < features.length; j++) {
            v += beta[j + 1] * features[j];
        }
        return v;
    }

    /** [절편, 계수1..p]. 학습 전이면 null. */
    public double[] coefficients() {
        return beta;
    }

    /** 부분 피벗 가우스 소거로 A β = b 를 푼다. */
    static double[] solve(double[][] aIn, double[] bIn) {
        int n = bIn.length;
        double[][] a = new double[n][n];
        double[] b = bIn.clone();
        for (int i = 0; i < n; i++) {
            a[i] = aIn[i].clone();
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp;
            double t = b[col]; b[col] = b[pivot]; b[pivot] = t;

            double diag = a[col][col];
            if (Math.abs(diag) < 1e-12) {
                continue; // 특이행렬 방어(릿지로 거의 발생 안 함)
            }
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                double factor = a[r][col] / diag;
                for (int c = col; c < n; c++) {
                    a[r][c] -= factor * a[col][c];
                }
                b[r] -= factor * b[col];
            }
        }
        double[] beta = new double[n];
        for (int i = 0; i < n; i++) {
            beta[i] = Math.abs(a[i][i]) < 1e-12 ? 0.0 : b[i] / a[i][i];
        }
        return beta;
    }
}
