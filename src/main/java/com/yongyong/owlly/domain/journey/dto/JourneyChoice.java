package com.yongyong.owlly.domain.journey.dto;

import com.yongyong.owlly.domain.journey.entity.JourneyChoiceMode;
import jakarta.validation.constraints.NotNull;

/**
 * 막차 또는 혼자 택시 선택 요청 (docs/api.md 5절 JourneyChoice).
 * 예) 매칭에 실패한 학생이 "막차로 갈게요"를 누르면 {"mode": "BUS"}, "혼자 택시"면 {"mode": "SOLO"}
 */
public record JourneyChoice(
        @NotNull JourneyChoiceMode mode   // 선택한 귀가 수단: BUS(막차) / SOLO(혼자 택시). 탑승 성공은 직접 입력할 수 없다
) {}
