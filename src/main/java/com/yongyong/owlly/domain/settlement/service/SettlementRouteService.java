package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.entity.PartyMember;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.party.service.EncryptedValueReader;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.settlement.dto.RoutePatch;
import com.yongyong.owlly.domain.settlement.dto.SettlementResponse;
import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.repository.SettlementRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 전 실제 하차 순서 수정 (API-36). 대표만.
 *
 * <p>대표가 실제로 내린 순서를 고치면, 서버가 저장된 하차 핀으로 도로 구간거리를 다시 계산하고
 * route_version 을 올린 뒤 예상 정산을 재계산한다(이미 받은 선송금은 보존 — {@link SettlementEstimateService#reEstimate}).
 * 클라이언트가 거리·분담액을 보내지 않고, 마지막 하차자(대표) 변경은 정책 확정 전까지 막는다.
 */
@Service
@RequiredArgsConstructor
public class SettlementRouteService {

    private final SettlementRepository settlements;
    private final RidePartyRepository rideParties;
    private final PartyStopRepository partyStops;
    private final PartyMemberRepository partyMembers;
    private final RideRequestRepository rideRequests;
    private final PickupPointRepository pickupPoints;
    private final TaxiFareService taxiFareService;
    private final EncryptedValueReader encryptedValueReader;
    private final SettlementEstimateService settlementEstimateService;
    private final SettlementService settlementService;

    /**
     * 하차 순서를 바꿔 경로·예상 정산을 다시 계산한다 (API-36).
     *
     * @param callerMemberId 요청자의 팟 참여 멤버 ID (대표여야 함)
     * @param ifMatch        수정 직전 조회한 정산 ETag. 그 사이 상태가 바뀌었으면 412
     * @return 재계산된 정산 상태
     */
    @Transactional
    public SettlementResponse editRoute(Long partyId, Long callerMemberId, RoutePatch patch, String ifMatch) {
        Settlement settlement = settlements.findByPartyId(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.SETTLEMENT_NOT_READY));
        RideParty party = rideParties.findById(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(party.getRepresentativeMemberId(), callerMemberId)) {
            throw new OwllyException(ErrorCode.FORBIDDEN); // 대표만 수정 가능
        }
        if (settlement.getFinalizedAt() != null) {
            throw new OwllyException(ErrorCode.SETTLEMENT_FINALIZED);
        }

        SettlementResponse current = settlementService.getSettlement(partyId, callerMemberId);
        if (ifMatch != null && !ETags.matches(ifMatch, current)) {
            throw new OwllyException(ErrorCode.PRECONDITION_FAILED);
        }
        if (patch.expectedRouteVersion() != settlement.getRouteVersion()) {
            throw new OwllyException(ErrorCode.ROUTE_VERSION_CONFLICT);
        }

        // 현재 계산 대상 멤버(현재 경로 버전의 하차 순서)와 요청한 순서가 같은 집합인지 검증.
        List<PartyStop> currentStops = partyStops
                .findByPartyIdAndRouteVersionOrderByStopOrderAsc(partyId, settlement.getRouteVersion());
        Set<Long> currentMemberIds = currentStops.stream().map(PartyStop::getMemberId).collect(Collectors.toSet());
        List<Long> orderedMemberIds = parseMemberIds(patch.orderedMemberIds());
        if (new HashSet<>(orderedMemberIds).size() != orderedMemberIds.size()
                || !new HashSet<>(orderedMemberIds).equals(currentMemberIds)) {
            throw new OwllyException(ErrorCode.INVALID_STOP_ORDER);
        }
        // 마지막 하차자(대표) 변경은 정책 확정 전까지 차단.
        if (!orderedMemberIds.get(orderedMemberIds.size() - 1).equals(party.getRepresentativeMemberId())) {
            throw new OwllyException(ErrorCode.REPRESENTATIVE_CHANGE_REQUIRES_POLICY);
        }

        Instant now = Instant.now();
        RouteEstimate route = recomputeRoute(party, orderedMemberIds);
        int newRouteVersion = settlement.getRouteVersion() + 1;
        List<PartyStop> newStops = new ArrayList<>(orderedMemberIds.size());
        for (int i = 0; i < orderedMemberIds.size(); i++) {
            newStops.add(PartyStop.builder()
                    .partyId(partyId)
                    .memberId(orderedMemberIds.get(i))
                    .routeVersion(newRouteVersion)
                    .stopOrder(i + 1)
                    .segmentDistanceM(route.segmentDistancesM().get(i))
                    .routingSource(route.routingSource())
                    .calculatedAt(now)
                    .build());
        }
        partyStops.saveAll(newStops);
        party.changeRoute(newRouteVersion);
        // 예상 정산 재계산(이미 받은 선송금은 보존). route_version·estimated_total 갱신 포함.
        settlementEstimateService.reEstimate(partyId, route.totalFareWon());

        return settlementService.getSettlement(partyId, callerMemberId);
    }

    /** 요청한 하차 순서대로 저장된 하차 핀으로 도로 구간거리를 다시 계산한다. */
    private RouteEstimate recomputeRoute(RideParty party, List<Long> orderedMemberIds) {
        Map<Long, Long> requestIdByMemberId = partyMembers.findByPartyIdOrderByIdAsc(party.getId()).stream()
                .collect(Collectors.toMap(PartyMember::getId, PartyMember::getRequestId));
        Map<Long, RideRequest> requestById = rideRequests.findAllById(orderedMemberIds.stream()
                        .map(requestIdByMemberId::get).toList()).stream()
                .collect(Collectors.toMap(RideRequest::getId, request -> request));

        PickupPoint pickupPoint = pickupPoints.findById(party.getPickupPointId())
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        GeoPoint origin = new GeoPoint(pickupPoint.getLatitude().doubleValue(), pickupPoint.getLongitude().doubleValue());
        List<GeoPoint> stops = orderedMemberIds.stream()
                .map(memberId -> encryptedValueReader.readDropoff(requestById.get(requestIdByMemberId.get(memberId))))
                .toList();
        return taxiFareService.estimateRoute(origin, stops, party.getDepartureAt());
    }

    private List<Long> parseMemberIds(List<String> rawMemberIds) {
        try {
            return rawMemberIds.stream().map(Long::valueOf).toList();
        } catch (NumberFormatException e) {
            throw new OwllyException(ErrorCode.INVALID_STOP_ORDER);
        }
    }
}
