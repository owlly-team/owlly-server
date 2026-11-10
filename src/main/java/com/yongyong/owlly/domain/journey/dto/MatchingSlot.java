package com.yongyong.owlly.domain.journey.dto;

import java.time.Instant;

/**
 * 출발 시간대 한 칸 (docs/api.md 5절 MatchingSlot).
 * 출발 시간 선택 화면(S-03)이 시간 칩마다 "지금 대기 인원"을 보여 줄 때 쓴다.
 * 예) 23:30 칩 → waitingCount 2 이면 "2명 대기", 출발 15분 전이 지난 칩은 registrationAllowed false.
 */
public record MatchingSlot(
        Instant departureAt,          // 서버가 허용한 30분 단위 출발 시각
        int waitingCount,             // 나와 같은 팟에 들어갈 수 있는 대기 인원(나 제외). 0이면 0명
        boolean registrationAllowed,  // 지금 이 시간대에 등록할 수 있는지
        String reason                 // 등록할 수 없는 이유 코드. 등록할 수 있으면 null (REGISTRATION_CLOSED: 출발 15분 전이 지남)
) {}
