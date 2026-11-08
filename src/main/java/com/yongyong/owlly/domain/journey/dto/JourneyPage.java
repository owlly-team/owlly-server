package com.yongyong.owlly.domain.journey.dto;

import java.util.List;

/**
 * 내 귀가 기록 한 페이지.
 * nextCursor 를 다음 요청의 cursor 로 보내면 더 이전 귀가를 이어서 받는다. 마지막 페이지면 null.
 */
public record JourneyPage(List<JourneyResponse> journeys, String nextCursor, boolean hasMore) {}
