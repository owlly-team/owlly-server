package com.yongyong.owlly.domain.party.dto;

import com.yongyong.owlly.domain.party.entity.BoardingStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 본인 탑승·미탑승 기록 요청 (docs/api.md 5절 BoardingRequest).
 * 예) 채팅방 탑승 확인에서 "탔어요" → {"status": "BOARDED"}, "못 탔어요" → {"status": "MISSED"}
 */
public record BoardingRequest(
        @NotNull BoardingStatus status   // BOARDED(탔어요) / MISSED(못 탔어요). UNKNOWN 은 서비스에서 422
) {}
