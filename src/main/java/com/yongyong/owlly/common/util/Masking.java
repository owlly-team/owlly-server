package com.yongyong.owlly.common.util;

/**
 * 민감 문자열 표시용 마스킹. 저장용 암호화와 달리 "가려서 보여주기"만 한다.
 * 계좌 등록 응답(MaskedAccount)·수취 계좌 조회 응답(RecipientAccount) 등에서 공용으로 쓴다.
 */
public final class Masking {

    private Masking() {
    }

    /** 계좌번호: 숫자만 남긴 뒤 앞 3자리·뒤 4자리만 보이고 가운데를 * 로. 예) 110*****7890 */
    public static String accountNumber(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() <= 7) {
            return "*".repeat(digits.length());
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "*".repeat(digits.length() - 7) + tail;
    }

    /** 예금주: 가운데 글자를 * 로. 2글자면 뒷글자만 가린다. 예) 홍*동, 안*현 */
    public static String holderName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
