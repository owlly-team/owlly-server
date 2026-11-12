package com.yongyong.owlly.domain.demand.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 노선 후보 목록의 "여기까지 봤어요" 표시(cursor).
 *
 * <p>정렬이 (유효순위, id)라서 커서도 그 두 값을 담는다.
 * 유효순위 = 후보면 candidate_rank, 비후보면 Integer.MAX_VALUE(목록 맨 뒤). 앱이 해석하지 않도록 Base64 로 감싼다.
 */
record DemandCursor(int effectiveRank, long id) {

    static final int NON_CANDIDATE_RANK = Integer.MAX_VALUE;

    static int effectiveRank(DemandEstimate e) {
        return e.getCandidateRank() == null ? NON_CANDIDATE_RANK : e.getCandidateRank();
    }

    static String encode(DemandEstimate last) {
        String raw = effectiveRank(last) + ":" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 앱이 보낸 cursor 를 해석한다. 망가진 값이면 422 VALIDATION_ERROR. */
    static DemandCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new DemandCursor(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR, List.of("cursor: 형식이 올바르지 않습니다."));
        }
    }
}
