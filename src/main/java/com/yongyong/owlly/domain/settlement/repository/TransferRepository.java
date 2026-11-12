package com.yongyong.owlly.domain.settlement.repository;

import com.yongyong.owlly.domain.settlement.entity.Transfer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 수동 이체(선송금·환급) 조회·상태 변경 (API-33 조회, API-39·40 송금 확인).
 *
 * <p>개인 항목마다 이체가 여러 개(선송금 PREPAY, 환급 REFUND)일 수 있다.
 * 항목을 하나씩 N번 조회하지 않도록, 항목 ID 목록을 한 번에 IN 으로 가져온 뒤
 * 서비스에서 항목별로 묶는다(N+1 쿼리 방지).
 */
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** 여러 정산 항목에 걸린 이체를 한 번에(오름차순). */
    List<Transfer> findBySettlementItemIdInOrderByIdAsc(List<Long> settlementItemIds);

    /**
     * 상태 변경(보냈어요/받았어요)을 위해 이체를 잠그고 조회한다.
     * 송금 확인과 매칭·정산 작업이 동시에 같은 이체를 건드리지 못하게 PESSIMISTIC_WRITE 로 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> findByIdForUpdate(@Param("id") Long id);

    /**
     * 이체가 속한 팟 ID. 이체 -> 정산 항목 -> 정산 -> 팟 순으로 잇는다.
     * 예) 송금자/수취자 권한을 확인할 때, 세션 사용자가 그 팟의 참여자인지 보려고 팟을 먼저 구한다.
     */
    @Query("""
            select s.partyId from Transfer t
            join SettlementItem i on i.id = t.settlementItemId
            join Settlement s on s.id = i.settlementId
            where t.id = :transferId
            """)
    Optional<Long> findPartyIdById(@Param("transferId") Long transferId);
}
