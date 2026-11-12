package com.yongyong.owlly.domain.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.settlement.dto.Bank;
import org.junit.jupiter.api.Test;

/** 수취 계좌 은행 기준 목록(API-03). 외부 기준정보 없이 코드로 관리하는 목록의 무결성을 본다. */
class BanksTest {

    @Test
    void 목록은_비어있지_않고_코드가_중복되지_않는다() {
        assertThat(Banks.ALL).isNotEmpty();
        assertThat(Banks.ALL).extracting(Bank::code).doesNotHaveDuplicates();
        assertThat(Banks.ALL).allSatisfy(bank -> {
            assertThat(bank.code()).isNotBlank();
            assertThat(bank.name()).isNotBlank();
        });
    }

    @Test
    void 등록_검증용_containsCode_는_있는_코드만_참이다() {
        assertThat(Banks.containsCode("004")).isTrue();   // KB국민은행
        assertThat(Banks.containsCode("090")).isTrue();   // 카카오뱅크
        assertThat(Banks.containsCode("999")).isFalse();  // 없는 코드
        assertThat(Banks.containsCode("")).isFalse();
    }

    @Test
    void 금융결제원_표준코드_예시가_올바른_은행명과_매칭된다() {
        assertThat(Banks.ALL).contains(new Bank("004", "KB국민은행"), new Bank("088", "신한은행"));
    }
}
