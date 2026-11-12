package com.yongyong.owlly.domain.settlement.repository;

import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 개인별 정산 항목 조회 (API-33).
 *
 * <p>한 정산(settlement) 아래의 멤버별 부담·환급 행들. settlement_id 로 모아서
 * ID 오름차순(=멤버 추가 순서)으로 돌려준다.
 */
public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    /** 이 정산에 속한 개인 항목 전체(오름차순). */
    List<SettlementItem> findBySettlementIdOrderByIdAsc(Long settlementId);
}
