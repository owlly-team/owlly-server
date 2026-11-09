package com.yongyong.owlly.domain.university;

import java.util.List;
import java.util.Map;

/** 현재 서비스 대상 대학과 허용 이메일 도메인. */
public final class SupportedUniversities {
    public static final List<String> NAMES = List.of(
            "강남대학교", "경희대학교", "단국대학교", "명지대학교", "한국외국어대학교");
    public static final Map<String, String> EMAIL_DOMAINS = Map.of(
            "강남대학교", "kangnam.ac.kr",
            "경희대학교", "khu.ac.kr",
            "단국대학교", "dankook.ac.kr",
            "명지대학교", "mju.ac.kr",
            "한국외국어대학교", "hufs.ac.kr");

    private SupportedUniversities() {}

    public static boolean contains(String name) { return NAMES.contains(name); }
}
