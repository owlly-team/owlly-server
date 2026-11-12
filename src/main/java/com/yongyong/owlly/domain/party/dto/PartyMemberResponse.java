package com.yongyong.owlly.domain.party.dto;

import com.yongyong.owlly.domain.party.entity.BoardingStatus;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.user.entity.Gender;

/**
 * 팟 참여자 한 명 (docs/api.md 5절 PartyMember).
 *
 * <p>매칭 결과 화면(S-05)의 멤버 목록 한 줄이 된다.
 * 예) "1번 · 김*연 · 단국대 · 동성 · 죽전1동 · 대표"
 *
 * <p>다른 학생의 개인정보는 이름 일부·학교·하차 동·성별만 보낸다.
 * 정확한 하차 위치, 실명, 계좌, 학생 계정 번호는 넣지 않는다.
 * memberId 는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 */
public record PartyMemberResponse(
        Long memberId,                  // 팟 참여 번호. 학생 계정 번호 대신 이 번호로 사람을 구분한다
        String maskedName,              // 가운데를 가린 이름. 예) 김*연
        String universityName,          // 학교 이름
        String destinationDongName,     // 하차 행정동 이름. 정확한 위치는 공개하지 않는다
        boolean sameGenderAsMe,         // 나와 성별이 같은지 (본인 줄은 true)
        Gender gender,                  // 성별 FEMALE / MALE. 동성 매칭을 끈 사람도 있어서 심야에 누구와 타는지 미리 알 수 있게 보여 주는 안전 정보
        boolean isRepresentative,       // 대표(마지막 하차자)인지
        MemberDecision decision,        // PENDING / ACCEPTED / DECLINED / LEFT / REMOVED_UNPAID
        BoardingStatus boardingStatus,  // UNKNOWN / BOARDED / MISSED
        Integer stopOrder               // 하차 순서(1부터). 현재 경로에 없으면 null
) {}
