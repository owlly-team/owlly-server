package com.yongyong.owlly.domain.party.dto;

/**
 * 팟 조회 응답의 allowedActions 값. 지금 이 학생이 누를 수 있는 버튼이다.
 * 프론트는 목록에 있는 버튼만 켜고, 없는 버튼은 비활성으로 보여 준다.
 *
 * <p>매칭 결과 화면(S-05) 버튼부터 넣었다. 대표의 택시 호출 확인·탑승 기록 버튼은
 * 해당 API를 만들 때 값을 추가한다.
 */
public enum PartyAction {

    /** "이 팟 확정하기" → 본인 팟 확정 (API-23). */
    CONFIRM,

    /** "빠지기" → 팟 거절·이탈 (API-24). */
    LEAVE
}
