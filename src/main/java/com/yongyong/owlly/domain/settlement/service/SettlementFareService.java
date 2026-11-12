package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.settlement.dto.FareInput;
import com.yongyong.owlly.domain.settlement.dto.FarePreview;
import com.yongyong.owlly.domain.settlement.dto.FarePreviewItem;
import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import com.yongyong.owlly.domain.settlement.repository.SettlementItemRepository;
import com.yongyong.owlly.domain.settlement.repository.SettlementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 요금 정산 (API-37 미리보기, API-38 확정).
 *
 * <p>탑승 후 대표가 실제 택시요금을 입력하면, 선송금과 같은 거리 비례 방식으로 멤버별 최종 분담액을
 * 다시 계산한다. 차액(선송금 − 확정)이 양수면 대표가 돌려주고(환급), 음수면 멤버가 더 보낸다(추가 납부).
 */
@Service
@RequiredArgsConstructor
public class SettlementFareService {

    private final SettlementRepository settlements;
    private final SettlementItemRepository settlementItems;
    private final RidePartyRepository rideParties;
    private final PartyStopRepository partyStops;
    private final FareShareCalculator fareShareCalculator;

    /**
     * 실제 요금 분담·환급 미리보기 (API-37). 저장하지 않는다.
     *
     * @param partyId        정산할 팟
     * @param callerMemberId 요청자의 팟 참여 멤버 ID (대표여야 함)
     * @param input          실제 요금·경로 버전
     */
    @Transactional(readOnly = true)
    public FarePreview preview(Long partyId, Long callerMemberId, FareInput input) {
        Context context = loadForActualFare(partyId, callerMemberId, input);

        int[] shares = fareShareCalculator.shares(context.stops(), input.actualTotalWon());
        Map<Long, Integer> prepayByMemberId = context.items().stream()
                .collect(Collectors.toMap(SettlementItem::getMemberId, SettlementItem::getPrepayDueWon));

        List<FarePreviewItem> items = new ArrayList<>(context.stops().size());
        for (int i = 0; i < context.stops().size(); i++) {
            Long memberId = context.stops().get(i).getMemberId();
            boolean representative = memberId.equals(context.representativeMemberId());
            int actualShare = shares[i];
            int refundDue = representative ? 0 : prepayByMemberId.getOrDefault(memberId, 0) - actualShare;
            items.add(new FarePreviewItem(String.valueOf(memberId), actualShare, refundDue));
        }

        // 추가 납부(음수 차액)를 허용하므로 확정을 막지 않는다(기획 확정: 디자인 따름).
        return new FarePreview(input.actualTotalWon(), context.settlement().getRouteVersion(), items, true, List.of());
    }

    /**
     * 실제 요금 계산에 필요한 정산·대표·하차 순서를 모으고 공통 검증을 수행한다.
     * (대표 권한, 확정 여부, 경로 버전, 요금 상한, 구간 거리)
     */
    private Context loadForActualFare(Long partyId, Long callerMemberId, FareInput input) {
        Settlement settlement = settlements.findByPartyId(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.SETTLEMENT_NOT_READY));
        if (settlement.getFinalizedAt() != null) {
            throw new OwllyException(ErrorCode.SETTLEMENT_FINALIZED);
        }

        RideParty party = rideParties.findById(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(party.getRepresentativeMemberId(), callerMemberId)) {
            throw new OwllyException(ErrorCode.FORBIDDEN); // 대표만 정산 가능
        }
        if (input.routeVersion() != settlement.getRouteVersion()) {
            throw new OwllyException(ErrorCode.ROUTE_VERSION_CONFLICT);
        }
        // 실제 요금 상한: 예상 전체 요금 × 1.2 (floor). 선송금 버퍼와 동일한 상한.
        long cap = (long) settlement.getEstimatedTotalWon() * 6 / 5;
        if (input.actualTotalWon() > cap) {
            throw new OwllyException(ErrorCode.FARE_LIMIT_EXCEEDED);
        }

        List<PartyStop> stops = partyStops
                .findByPartyIdAndRouteVersionOrderByStopOrderAsc(partyId, settlement.getRouteVersion());
        if (stops.isEmpty() || fareShareCalculator.totalDistanceM(stops) <= 0) {
            throw new OwllyException(ErrorCode.INVALID_ROUTE_DISTANCE);
        }

        List<SettlementItem> items = settlementItems.findBySettlementIdOrderByIdAsc(settlement.getId());
        Long representativeMemberId = stops.get(stops.size() - 1).getMemberId();
        return new Context(settlement, party, stops, items, representativeMemberId);
    }

    private record Context(Settlement settlement, RideParty party, List<PartyStop> stops,
                           List<SettlementItem> items, Long representativeMemberId) {
    }
}
