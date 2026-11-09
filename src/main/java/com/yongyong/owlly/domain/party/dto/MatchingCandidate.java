package com.yongyong.owlly.domain.party.dto;

import com.yongyong.owlly.domain.user.entity.Gender;

/**
 * 매칭 작업이 팟으로 묶을 대기 신청 한 건.
 * 같은 그룹(학교·거점·출발 시각·도착 구)에 속한 신청만 모아서 만든다.
 */
public record MatchingCandidate(
        Long requestId,          // 동승 신청 (ride_requests.id)
        Long userId,             // 신청한 학생 (journeys.user_id)
        Gender gender,           // 신청 당시 성별
        boolean sameGenderOnly   // 동성끼리만 타고 싶은지
) {

    /** 두 신청이 같은 팟에 들어갈 수 있는 성별 조합인지. 한쪽이라도 동성만 원하면 성별이 같아야 한다. */
    public boolean genderCompatibleWith(MatchingCandidate other) {
        if (sameGenderOnly || other.sameGenderOnly) {
            return gender == other.gender;
        }
        return true;
    }
}
