package com.yongyong.owlly.domain.demand.publicdata;

/**
 * 수요 산출에 쓰는 공공데이터 실측 요약(월 단위, 용인 기준). 못 받은 항목은 null 이고, 그 지표는 추정치로 표시한다.
 *
 * @param taxiRequestCount 용인앱택시 월 호출 건수 합
 * @param taxiMatchedCount 용인앱택시 월 탑승(배차 성사) 건수 합
 * @param nightBusRiders   용인 심야(23시~) 버스 이용객 합
 */
public record PublicDemandStats(Long taxiRequestCount, Long taxiMatchedCount, Long nightBusRiders) {

    public static PublicDemandStats empty() {
        return new PublicDemandStats(null, null, null);
    }

    public boolean hasTaxi() {
        return taxiRequestCount != null && taxiMatchedCount != null;
    }

    public boolean hasNightBus() {
        return nightBusRiders != null;
    }

    /** 택시 미충족 비율 = (호출 − 탑승) ÷ 호출. 호출이 0이거나 데이터가 없으면 null. */
    public Double taxiUnmetRate() {
        if (!hasTaxi() || taxiRequestCount == 0) {
            return null;
        }
        return (double) (taxiRequestCount - taxiMatchedCount) / taxiRequestCount;
    }
}
