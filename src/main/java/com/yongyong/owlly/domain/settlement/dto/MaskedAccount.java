package com.yongyong.owlly.domain.settlement.dto;

import com.yongyong.owlly.common.util.Masking;
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
                Masking.accountNumber(plainAccountNumber),
                Masking.holderName(plainHolderName),
                account.getDeleteAfter());
    }
}
