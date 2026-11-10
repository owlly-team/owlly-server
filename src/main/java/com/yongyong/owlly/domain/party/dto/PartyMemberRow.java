package com.yongyong.owlly.domain.party.dto;

import com.yongyong.owlly.domain.party.entity.BoardingStatus;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.user.entity.Gender;

/**
 * 팟 조회용으로 한 번에 읽어 온 참여자 한 명의 값 (서버 안에서만 쓴다).
 * 참여 → 신청 → 귀가 → 학생 프로필·학교·하차 동을 이어서 읽는다.
 * 실명은 암호화된 그대로라 응답으로 내보내기 전에 복호화·마스킹한다.
 */
public record PartyMemberRow(
        Long memberId,                 // 팟 참여 번호 (party_members.id)
        Long userId,                   // 학생 번호. 내가 누구인지 찾을 때만 쓰고 응답에는 넣지 않는다
        MemberDecision decision,       // 참여 결정 (확정 대기·확정·거절·이탈 등)
        BoardingStatus boardingStatus, // 탑승 확인 (모름·탔음·못 탔음)
        Gender genderSnapshot,         // 신청할 때의 성별
        String realNameEncrypted,      // 암호화된 실명
        String universityName,         // 신청할 때의 학교 이름
        String destinationDongName     // 하차 행정동 이름
) {}
