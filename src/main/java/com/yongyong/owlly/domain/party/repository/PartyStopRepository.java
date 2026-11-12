package com.yongyong.owlly.domain.party.repository;

import com.yongyong.owlly.domain.party.entity.PartyStop;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 팟의 하차 순서·구간 거리 저장·조회. 하차 순서·인원이 바뀌면 새 route_version 으로 다시 저장한다.
 */
public interface PartyStopRepository extends JpaRepository<PartyStop, Long> {

    /**
     * 한 경로 버전의 하차 순서(1번부터).
     * 예) 매칭 결과 화면의 "내리는 순서", 정산의 구간 거리 비례 계산.
     */
    List<PartyStop> findByPartyIdAndRouteVersionOrderByStopOrderAsc(Long partyId, int routeVersion);
}
