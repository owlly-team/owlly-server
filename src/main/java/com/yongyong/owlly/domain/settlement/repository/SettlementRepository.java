package com.yongyong.owlly.domain.settlement.repository;

import com.yongyong.owlly.domain.settlement.entity.Settlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 팟 정산 조회 (API-33 GET /parties/{partyId}/settlement).
 *
 * <p>정산은 팟당 1개(settlements.party_id UNIQUE)라서 partyId 로 단건을 찾는다.
 * 아직 정산이 만들어지지 않았으면 비어 있고(Optional.empty), 서비스에서 409 로 처리한다.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** 이 팟의 정산 1건. 생성 전이면 비어 있음. */
    Optional<Settlement> findByPartyId(Long partyId);
}
