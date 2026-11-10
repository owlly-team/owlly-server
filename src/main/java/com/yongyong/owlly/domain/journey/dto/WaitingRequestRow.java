package com.yongyong.owlly.domain.journey.dto;

import com.yongyong.owlly.domain.user.entity.Gender;
import java.time.Instant;

/**
 * 대기 중인 신청 한 건에서 슬롯 대기 인원을 세는 데 필요한 값만 모은 것(서버 안에서만 쓴다).
 * 예) 23:30 에 같은 거점·같은 구로 기다리는 다른 학생 한 명.
 */
public record WaitingRequestRow(
        Instant departureAt,    // 신청한 출발 시각(슬롯)
        Long userId,            // 신청한 학생
        Gender gender,          // 신청 당시 성별
        boolean sameGenderOnly  // 동성끼리만 타고 싶은지
) {}
