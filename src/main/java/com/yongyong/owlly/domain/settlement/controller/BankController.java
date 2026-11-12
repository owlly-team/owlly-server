package com.yongyong.owlly.domain.settlement.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.domain.settlement.Banks;
import com.yongyong.owlly.domain.settlement.dto.Bank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수취 계좌 은행 목록 (API-03).
 *
 * <p>계좌 등록·조회 화면에서 은행 선택지로 쓴다. 고정 기준 목록이라 별도 서비스 없이 바로 돌려준다.
 */
@RestController
public class BankController {

    /** API-03 수취 계좌 은행 목록. */
    @GetMapping("/api/v1/banks")
    public ApiResponse<List<Bank>> getBanks() {
        return ApiResponse.of(Banks.ALL);
    }
}
