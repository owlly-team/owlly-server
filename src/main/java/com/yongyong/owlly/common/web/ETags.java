package com.yongyong.owlly.common.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 응답 내용으로 ETag 를 만든다 (docs/api.md 1절 "ETag 는 관련 레코드 상태를 함께 반영하는 강한 검증자").
 *
 * <p>응답 record 의 문자열을 SHA-256 으로 줄인 값이라, 응답에 보이는 값이 하나라도 바뀌면 ETag 도 바뀐다.
 * 예) 팟 조회 → 다른 멤버가 확정 → 다시 조회하면 ETag 가 달라진다. 별도 버전 컬럼을 두지 않는다.
 */
public final class ETags {

    private ETags() {
    }

    /** 응답 객체의 ETag. 예) "\"3f2a9c...\"" (따옴표 포함) */
    public static String of(Object response) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(response).getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** If-Match 값이 지금 응답의 ETag 와 같은지. 따옴표를 빼고 보내도 같은 값으로 본다. */
    public static boolean matches(String ifMatch, Object currentResponse) {
        String expected = of(currentResponse);
        String given = ifMatch.trim();
        return given.equals(expected) || ("\"" + given + "\"").equals(expected);
    }
}
