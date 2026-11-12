package com.yongyong.owlly.domain.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 수취 계좌 등록 요청 (docs/api.md 5절 AccountInput).
 *
 * <p>API-31 의 요청 본문. 계좌번호·예금주는 저장 시 암호화되고, 응답에선 마스킹되어 나간다.
 * 값 자체의 형식(숫자만 등)은 은행별로 달라 길이만 제한하고, 공백·하이픈 정규화는 서비스에서 한다.
 */
public record AccountInput(

        @NotBlank(message = "은행 코드는 필수입니다.")
        @Size(max = 16, message = "은행 코드는 16자 이하여야 합니다.")
        String bankCode,

        @NotBlank(message = "계좌번호는 필수입니다.")
        @Size(max = 40, message = "계좌번호는 40자 이하여야 합니다.")
        String accountNumber,

        @NotBlank(message = "예금주는 필수입니다.")
        @Size(max = 50, message = "예금주는 50자 이하여야 합니다.")
        String holderName
) {
}
