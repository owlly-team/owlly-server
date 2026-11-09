package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.domain.settlement.dto.MemberEstimate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예상 정산 만들기 (호출 껍데기).
 *
 * <p>상황: 매칭 작업이 팟·멤버·하차 순서(party_stops, 구간 거리 포함)를 저장한 직후 같은 트랜잭션에서 부른다.
 * 홈 카드·매칭 결과 화면(S-05)·매칭 알림에 예상 1인 분담액과 대표에게 보낼 금액이 바로 보여야 하기 때문이다.
 *
 * <p>내용은 정산 담당(안시현)이 채우기로 했다. 채우기 전까지는 빈 목록을 돌려주고,
 * 매칭 알림은 금액 없이 나간다.
 */
@Service
public class SettlementEstimateService {

    /**
     * 팟의 예상 정산을 만들고 멤버별 예상 금액을 돌려준다.
     *
     * @param partyId           매칭 작업이 만든 팟
     * @param estimatedTotalWon 전체 예상 택시비(원). TaxiFareService 결과(심야할증·통행료 포함)
     * @return 멤버별 예상 1인 분담액·선송금. 아직 구현 전이면 빈 목록
     */
    @Transactional
    public List<MemberEstimate> createEstimate(Long partyId, int estimatedTotalWon) {
        return List.of();
    }
}
