package com.yongyong.owlly.domain.settlement.dto;

import com.yongyong.owlly.common.util.Masking;
import com.yongyong.owlly.domain.settlement.entity.RoomAccount;

/**
 * 송금할 수취 계좌 응답 (docs/api.md 5절 RecipientAccount).
 *
 * <p>API-32 결과. 해당 이체의 송금자에게만 제공한다. 돈을 실제로 보내야 하므로
 * 계좌번호는 복호화한 원문으로 주고, 예금주는 본인 확인용으로 마스킹한다.
 * (Cache-Control: no-store 로 내려 캐시에 남지 않게 한다.)
 */
public record RecipientAccount(
        Long accountId,
        String bankCode,
        String accountNumber,       // 복호화된 원문 (인가된 송금자에게만)
        String maskedHolderName,    // 마스킹된 예금주
        String purpose              // PREPAY_RECEIVE / REFUND_RECEIVE
) {

    /** 저장된 계좌 + 복호화한 원문으로 응답을 만든다. 계좌번호는 원문, 예금주는 가린다. */
    public static RecipientAccount of(RoomAccount account, String plainAccountNumber, String plainHolderName) {
        return new RecipientAccount(
                account.getId(),
                account.getBankCode(),
                plainAccountNumber,
                Masking.holderName(plainHolderName),
                account.getPurpose().name());
    }
}
