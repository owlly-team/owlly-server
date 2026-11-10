package com.yongyong.owlly.domain.journey.entity;

/**
 * 학생이 귀가 선택 화면에서 직접 고를 수 있는 수단 (api.md JourneyChoice.mode).
 * 동승 탑승(POOL_BOARDED)은 채팅방 "탔어요"로만 기록되므로 여기에 없다.
 */
public enum JourneyChoiceMode {
    BUS(JourneyOutcome.BUS_SELECTED),   // 막차 선택
    SOLO(JourneyOutcome.SOLO_SELECTED); // 혼자 택시 선택

    private final JourneyOutcome outcome;

    JourneyChoiceMode(JourneyOutcome outcome) {
        this.outcome = outcome;
    }

    /** 이 선택이 귀가 결과로 기록될 때의 값. 예) BUS → BUS_SELECTED */
    public JourneyOutcome outcome() {
        return outcome;
    }
}
