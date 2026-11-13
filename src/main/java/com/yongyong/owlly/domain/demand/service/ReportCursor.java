package com.yongyong.owlly.domain.demand.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.demand.entity.StakeholderReport;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 내 리포트 목록의 "여기까지 봤어요" 표시(cursor). 최신순이라 마지막으로 본 리포트 id 를 담는다.
 */
record ReportCursor(long id) {

    static String encode(StakeholderReport last) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(last.getId()).getBytes(StandardCharsets.UTF_8));
    }

    /** 앱이 보낸 cursor 를 해석한다. 망가진 값이면 422 VALIDATION_ERROR. */
    static ReportCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return new ReportCursor(Long.parseLong(raw));
        } catch (IllegalArgumentException e) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR, List.of("cursor: 형식이 올바르지 않습니다."));
        }
    }
}
