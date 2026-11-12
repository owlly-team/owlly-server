package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.domain.party.entity.PartyStop;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 거리 비례(구간별 인원 분할) 분담액 계산기. 예상 정산(createEstimate)과 실제 정산(preview/finalize)이
 * 같은 규칙으로 계산하도록 한곳에 둔다.
 *
 * <p>하차 순서대로 stops[0..n-1]. 구간 k(0-based)는 stops[k] 가 내리는 구간이고, 그 구간엔 아직 내리지
 * 않은 (n-k)명이 타고 있다. 구간 요금은 거리 비례(total × d_k / D)이고 그 인원수로 균등 분할한다.
 * stops[j] 는 구간 0..j 를 타므로 그 합이 분담액. 반올림 잔차는 마지막 하차자(대표)가 흡수해 합계를
 * 전체 요금과 정확히 일치시킨다.
 */
@Component
public class FareShareCalculator {

    /**
     * 하차 순서(오름차순) stops 와 전체 요금 total 로 멤버별 분담액을 계산한다.
     *
     * @return stops 와 같은 순서의 분담액 배열. 합계는 total 과 일치
     */
    public int[] shares(List<PartyStop> stops, int total) {
        int n = stops.size();
        int[] shares = new int[n];

        long totalDistance = 0;
        for (PartyStop stop : stops) {
            totalDistance += stop.getSegmentDistanceM();
        }
        if (totalDistance <= 0) { // 거리 정보가 없으면 균등 분할로 대체
            int base = total / n;
            for (int i = 0; i < n; i++) {
                shares[i] = base;
            }
            shares[n - 1] += total - base * n;
            return shares;
        }

        long rounded = 0;
        for (int j = 0; j < n; j++) {
            double share = 0;
            for (int k = 0; k <= j; k++) {
                double segmentFare = (double) total * stops.get(k).getSegmentDistanceM() / totalDistance;
                share += segmentFare / (n - k); // 구간 k 에 탄 인원 = n - k
            }
            shares[j] = (int) Math.round(share);
            rounded += shares[j];
        }
        shares[n - 1] += (int) (total - rounded); // 잔차는 대표가 흡수 → 합계 정확히 일치
        return shares;
    }

    /** 구간 거리 합(m). 0 이면 거리 정보가 없어 분담을 계산할 수 없다. */
    public long totalDistanceM(List<PartyStop> stops) {
        long sum = 0;
        for (PartyStop stop : stops) {
            sum += stop.getSegmentDistanceM();
        }
        return sum;
    }
}
