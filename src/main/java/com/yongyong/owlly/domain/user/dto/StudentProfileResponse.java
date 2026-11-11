package com.yongyong.owlly.domain.user.dto;

import com.yongyong.owlly.domain.auth.dto.SessionResponse.CurrentUser;

/** 실명과 정확한 하차 좌표는 로그인한 본인에게만 반환한다. */
public record StudentProfileResponse(CurrentUser user, String realName, String genderCode,
        boolean sameGenderDefault, Long defaultDongId, String defaultDongName, Point defaultDropoff) {
    public record Point(double latitude, double longitude) {}
}
