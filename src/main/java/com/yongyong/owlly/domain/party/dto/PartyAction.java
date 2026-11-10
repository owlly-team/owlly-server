package com.yongyong.owlly.domain.party.dto;

/**
 * 팟 조회 응답의 allowedActions 값. 지금 이 학생이 누를 수 있는 버튼이다.
 * 프론트는 목록에 있는 버튼만 켜고, 없는 버튼은 비활성으로 보여 준다.
 */
public enum PartyAction {

    /** "이 팟 확정하기" → 본인 팟 확정 (API-23). */
    CONFIRM,

    /** "빠지기" → 팟 거절·이탈 (API-24). */
    LEAVE,

    /** 대표의 "택시를 불렀어요" → 대표의 외부 택시 호출 확인 (API-25). */
    CONFIRM_TAXI_CALL,

    /** 채팅방 탑승 확인의 "탔어요"·"못 탔어요" → 본인 탑승·미탑승 기록 (API-26). */
    RECORD_BOARDING
}
