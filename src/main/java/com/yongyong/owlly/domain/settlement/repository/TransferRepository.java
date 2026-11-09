package com.yongyong.owlly.domain.settlement.repository;

import com.yongyong.owlly.domain.settlement.entity.Transfer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 수동 이체(선송금·환급) 조회 (API-33).
 *
 * <p>개인 항목마다 이체가 여러 개(선송금 PREPAY, 환급 REFUND)일 수 있다.
 * 항목을 하나씩 N번 조회하지 않도록, 항목 ID 목록을 한 번에 IN 으로 가져온 뒤
 * 서비스에서 항목별로 묶는다(N+1 쿼리 방지).
 */
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** 여러 정산 항목에 걸린 이체를 한 번에(오름차순). */
    List<Transfer> findBySettlementItemIdInOrderByIdAsc(List<Long> settlementItemIds);
}
