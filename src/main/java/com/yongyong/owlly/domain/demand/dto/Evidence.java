package com.yongyong.owlly.domain.demand.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * 노선 후보 근거 지표 한 줄.
 *
 * <p>demand_estimates.evidence(jsonb 배열)의 각 항목을 그대로 노출한다.
 * 서비스 관측(OBSERVED)과 공공데이터 추정(ESTIMATED)을 구분해, 숫자의 출처·시점·단위를 함께 보여 준다.
 * (예: "동승 등록 86건 · 서비스 귀가 기록", "심야 버스 이용객 일 47명 · 경기도 시군별 버스 이용객수")
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Evidence(
        String sourceLabel,
        Instant asOf,
        String unit,
        String spatialResolution,
        String temporalResolution,
        String kind,
        String description) {}
