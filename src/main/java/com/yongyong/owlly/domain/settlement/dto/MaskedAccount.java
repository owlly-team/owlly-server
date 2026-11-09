package com.yongyong.owlly.domain.settlement.dto;

import com.yongyong.owlly.domain.settlement.entity.RoomAccount;
import java.time.Instant;

/**
 * 마스킹된 수취 계좌 응답 (docs/api.md 5절 MaskedAccount).
 *
 * <p>API-31 등록 결과. 원문 계좌번호·예금주는 절대 내보내지 않고 일부만 보이게 가린다.
 * 예) "110-234-567890" → "110******7890", "홍길동" → "홍*동"
 */
public record MaskedAccount(
        Long id,
        String purpose,                 // PREPAY_RECEIVE / REFUND_RECEIVE
        String bankCode,
        String maskedAccountNumber,     // 앞뒤 일부만 보이게 가린 계좌번호
        String maskedHolderName,        // 가운데를 가린 예금주
        Instant deleteAfter             // 방 종료 시 삭제 예정. 닫히기 전 null
) {

    /** 저장된 계좌 + 입력 원문(마스킹용)으로 응답을 만든다. 원문은 가린 뒤에만 쓴다. */
    public static MaskedAccount of(RoomAccount account, String plainAccountNumber, String plainHolderName) {
        return new MaskedAccount(
                account.getId(),
                account.getPurpose().name(),
                account.getBankCode(),
                maskAccountNumber(plainAccountNumber),
                maskHolderName(plainHolderName),
                account.getDeleteAfter());
    }

    /** 계좌번호: 하이픈·공백을 뺀 뒤 앞 3자리·뒤 4자리만 남기고 가운데를 * 로. */
    private static String maskAccountNumber(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() <= 7) {
            return "*".repeat(digits.length());
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "*".repeat(digits.length() - 7) + tail;
    }

    /** 예금주: 가운데 글자를 * 로. 2글자면 뒷글자만 가린다. */
    private static String maskHolderName(String name) {
        if (name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
