package com.yongyong.owlly.domain.settlement;

import com.yongyong.owlly.domain.settlement.dto.Bank;
import java.util.List;

/**
 * 수취 계좌로 쓸 수 있는 은행 기준 목록 (API-03).
 *
 * <p>ERD 에 은행 테이블을 두지 않기로 해(docs/api.md API-03), 외부 기준정보 대신 여기서 관리한다.
 * 코드는 금융결제원 표준 은행 코드를 따른다. 계좌 등록(API-31)의 {@code bankCode} 검증에도 재사용할 수 있다.
 */
public final class Banks {
    public static final List<Bank> ALL = List.of(
            new Bank("002", "KDB산업은행"),
            new Bank("003", "IBK기업은행"),
            new Bank("004", "KB국민은행"),
            new Bank("007", "Sh수협은행"),
            new Bank("011", "NH농협은행"),
            new Bank("020", "우리은행"),
            new Bank("023", "SC제일은행"),
            new Bank("027", "한국씨티은행"),
            new Bank("031", "DGB대구은행"),
            new Bank("032", "BNK부산은행"),
            new Bank("034", "광주은행"),
            new Bank("035", "제주은행"),
            new Bank("037", "전북은행"),
            new Bank("039", "BNK경남은행"),
            new Bank("045", "새마을금고"),
            new Bank("048", "신협"),
            new Bank("071", "우체국예금보험"),
            new Bank("081", "하나은행"),
            new Bank("088", "신한은행"),
            new Bank("089", "케이뱅크"),
            new Bank("090", "카카오뱅크"),
            new Bank("092", "토스뱅크"));

    private Banks() {}

    public static boolean containsCode(String code) {
        return ALL.stream().anyMatch(bank -> bank.code().equals(code));
    }
}
