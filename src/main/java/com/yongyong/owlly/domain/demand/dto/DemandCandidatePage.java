package com.yongyong.owlly.domain.demand.dto;

import java.util.List;

/**
 * 수요 지도·노선 후보 목록 한 페이지(API-43).
 *
 * <p>공통 {@code {data, meta}} 계약을 따르되, meta 에 어느 산출(runId)·어떤 모델(modelVersion)의 결과인지와
 * 다음 페이지 여부·커서를 함께 싣는다. nextCursor 를 다음 요청의 cursor 로 보내면 이어서 받는다(마지막이면 null).
 */
public record DemandCandidatePage(List<DemandCandidate> data, Meta meta) {

    public record Meta(String runId, String modelVersion, boolean hasMore, String nextCursor) {}

    public static DemandCandidatePage of(
            List<DemandCandidate> data, String runId, String modelVersion, boolean hasMore, String nextCursor) {
        return new DemandCandidatePage(data, new Meta(runId, modelVersion, hasMore, nextCursor));
    }
}
