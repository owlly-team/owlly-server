package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.settlement.dto.MemberEstimate;
import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import com.yongyong.owlly.domain.settlement.entity.SettlementItemStatus;
import com.yongyong.owlly.domain.settlement.entity.SettlementStatus;
import com.yongyong.owlly.domain.settlement.entity.Transfer;
import com.yongyong.owlly.domain.settlement.entity.TransferKind;
import com.yongyong.owlly.domain.settlement.entity.TransferStatus;
import com.yongyong.owlly.domain.settlement.repository.SettlementItemRepository;
import com.yongyong.owlly.domain.settlement.repository.SettlementRepository;
import com.yongyong.owlly.domain.settlement.repository.TransferRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예상 정산 만들기.
 *
 * <p>상황: 매칭 작업이 팟·멤버·하차 순서(party_stops, 구간 거리 포함)를 저장한 직후 같은 트랜잭션에서 부른다.
 * 홈 카드·매칭 결과 화면(S-05)·매칭 알림에 예상 1인 분담액과 대표에게 보낼 금액이 바로 보여야 하기 때문이다.
 *
 * <p>분담은 <b>거리 비례(구간별 인원 분할)</b>로 한다. 각 하차 구간 요금을 그 구간에 타고 있던 인원수로 나눠,
 * 먼저 내린 사람이 덜 내도록 한다. 선송금은 분담액의 120%(심야할증·통행료 불확실성 대비, Settlement 주석의 ×1.2
 * 상한과 동일)를 100원 단위로 올려 받고, 실제 요금 확정 후 차액을 환급한다. 대표(막차)는 선송금이 0.
 *
 * <p>이 메서드가 settlements·settlement_items·PENDING 선송금 transfers 를 함께 만든다(서버 내부 작업).
 */
@Service
@RequiredArgsConstructor
public class SettlementEstimateService {

    /** 선송금 버퍼: 분담액 대비 선송금 비율. 실제 요금 상한(Settlement ×1.2)과 맞춘다. */
    private static final double PREPAY_BUFFER = 1.2;

    private final RidePartyRepository rideParties;
    private final PartyStopRepository partyStops;
    private final SettlementRepository settlements;
    private final SettlementItemRepository settlementItems;
    private final TransferRepository transfers;
    private final FareShareCalculator fareShareCalculator;

    /**
     * 팟의 예상 정산을 만들고 멤버별 예상 금액을 돌려준다.
     *
     * <p>이미 정산이 있으면(매칭 재시도 등) 기존 항목을 그대로 돌려줘 중복 생성을 막는다.
     * 하차 순서가 아직 없으면 빈 목록을 돌려준다.
     *
     * @param partyId           매칭 작업이 만든 팟
     * @param estimatedTotalWon 전체 예상 택시비(원). TaxiFareService 결과(심야할증·통행료 포함)
     * @return 멤버별 예상 1인 분담액·선송금
     */
    @Transactional
    public List<MemberEstimate> createEstimate(Long partyId, int estimatedTotalWon) {
        var existing = settlements.findByPartyId(partyId);
        if (existing.isPresent()) {
            return toEstimates(existing.get().getId());
        }

        RideParty party = rideParties.findById(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        int routeVersion = party.getRouteVersion();
        List<PartyStop> stops = partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(partyId, routeVersion);
        if (stops.isEmpty()) {
            return List.of();
        }

        int lastIndex = stops.size() - 1;
        Long representativeMemberId = stops.get(lastIndex).getMemberId(); // 막차 = 대표
        int[] shares = fareShareCalculator.shares(stops, estimatedTotalWon);

        Settlement settlement = settlements.save(Settlement.builder()
                .partyId(partyId)
                .estimatedTotalWon(estimatedTotalWon)
                .routeVersion(routeVersion)
                .status(SettlementStatus.ESTIMATED)
                .build());

        List<MemberEstimate> estimates = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            Long memberId = stops.get(i).getMemberId();
            boolean representative = memberId.equals(representativeMemberId);
            int share = shares[i];
            int prepay = representative ? 0 : ceilTo100(share * PREPAY_BUFFER);

            SettlementItem item = settlementItems.save(SettlementItem.builder()
                    .settlementId(settlement.getId())
                    .memberId(memberId)
                    .estimatedShareWon(share)
                    .prepayDueWon(prepay)
                    .status(SettlementItemStatus.ESTIMATED)
                    .build());

            if (!representative) {
                transfers.save(Transfer.builder()
                        .settlementItemId(item.getId())
                        .kind(TransferKind.PREPAY)
                        .payerMemberId(memberId)
                        .payeeMemberId(representativeMemberId)
                        .amountWon(prepay)
                        .status(TransferStatus.PENDING)
                        .build());
            }
            estimates.add(new MemberEstimate(memberId, share, prepay));
        }
        return estimates;
    }

    /**
     * 멤버 이탈로 경로가 바뀐 뒤 남은 사람의 예상 정산을 다시 계산한다 (API-24 이탈 처리에서 호출).
     *
     * <p>호출 전제: 이탈 처리(PartyService)가 빠진 사람을 지운 새 하차 순서를 새 route_version 으로 이미
     * 저장하고 팟의 경로 버전을 올려 둔 상태다. 같은 트랜잭션에서 호출한다.
     *
     * <p>동작:
     * <ul>
     *   <li>남은 하차 순서로 분담액을 다시 계산하고 settlement·settlement_items 를 갱신한다.</li>
     *   <li>빠진 멤버의 항목은 지우고, 아직 안 보낸 PENDING 선송금은 취소한다.</li>
     *   <li>이미 보냈거나 받은 선송금(SENT/RECEIVED)은 금액을 바꾸지 않는다(기획: 기수령 선송금 보존).</li>
     * </ul>
     *
     * @param partyId           이탈이 일어난 팟
     * @param estimatedTotalWon 다시 계산한 전체 예상 요금(새 경로 기준)
     * @return 남은 멤버별 예상 분담·선송금. 정산이 없거나 하차 순서가 없으면 빈 목록
     */
    @Transactional
    public List<MemberEstimate> reEstimate(Long partyId, int estimatedTotalWon) {
        Settlement settlement = settlements.findByPartyId(partyId).orElse(null);
        if (settlement == null || settlement.getFinalizedAt() != null) {
            return settlement == null ? List.of() : toEstimates(settlement.getId());
        }
        RideParty party = rideParties.findById(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        int routeVersion = party.getRouteVersion();
        List<PartyStop> stops = partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(partyId, routeVersion);
        if (stops.isEmpty()) {
            return toEstimates(settlement.getId());
        }

        Long representativeMemberId = stops.get(stops.size() - 1).getMemberId();
        int[] shares = fareShareCalculator.shares(stops, estimatedTotalWon);

        List<SettlementItem> currentItems = settlementItems.findBySettlementIdOrderByIdAsc(settlement.getId());
        Map<Long, SettlementItem> itemByMemberId = currentItems.stream()
                .collect(Collectors.toMap(SettlementItem::getMemberId, item -> item));
        Map<Long, List<Transfer>> transfersByItemId = transfers
                .findBySettlementItemIdInOrderByIdAsc(currentItems.stream().map(SettlementItem::getId).toList())
                .stream().collect(Collectors.groupingBy(Transfer::getSettlementItemId));
        Set<Long> remainingMemberIds = stops.stream().map(PartyStop::getMemberId).collect(Collectors.toSet());

        // 1) 빠진 멤버 정리: 새 하차 순서에 없는 항목은 PENDING 선송금을 취소하고 항목을 지운다.
        for (SettlementItem item : currentItems) {
            if (!remainingMemberIds.contains(item.getMemberId())) {
                transfersByItemId.getOrDefault(item.getId(), List.of()).forEach(transfer -> {
                    if (transfer.isPending()) transfer.cancel();
                });
                settlementItems.delete(item);
            }
        }

        // 2) 남은 멤버 재계산. 이미 보낸/받은 선송금은 그대로 두고, PENDING 선송금만 새 금액으로 맞춘다.
        settlement.reEstimate(routeVersion, estimatedTotalWon);
        List<MemberEstimate> estimates = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            Long memberId = stops.get(i).getMemberId();
            SettlementItem item = itemByMemberId.get(memberId);
            int share = shares[i];
            List<Transfer> memberTransfers = transfersByItemId.getOrDefault(item.getId(), List.of());
            boolean prepaySettled = memberTransfers.stream().anyMatch(this::isSettledPrepay);

            int prepay;
            if (memberId.equals(representativeMemberId)) {
                prepay = 0;
            } else if (prepaySettled) {
                prepay = item.getPrepayDueWon(); // 기수령 선송금 보존
            } else {
                prepay = ceilTo100(share * PREPAY_BUFFER);
                int updated = prepay;
                memberTransfers.stream()
                        .filter(t -> t.getKind() == TransferKind.PREPAY && t.isPending())
                        .forEach(t -> t.changeAmount(updated));
            }
            item.reEstimate(share, prepay);
            estimates.add(new MemberEstimate(memberId, share, prepay));
        }
        return estimates;
    }

    /** 이미 보냈거나 받은 선송금인지. 이런 송금은 재계산으로 바꾸지 않는다. */
    private boolean isSettledPrepay(Transfer transfer) {
        return transfer.getKind() == TransferKind.PREPAY
                && (transfer.getStatus() == TransferStatus.SENT || transfer.getStatus() == TransferStatus.RECEIVED);
    }

    /** 정산의 현재 개인 항목을 예상 금액 목록으로 변환한다. */
    private List<MemberEstimate> toEstimates(Long settlementId) {
        return settlementItems.findBySettlementIdOrderByIdAsc(settlementId).stream()
                .map(item -> new MemberEstimate(item.getMemberId(), item.getEstimatedShareWon(), item.getPrepayDueWon()))
                .toList();
    }

    /** 100원 단위 올림. 선송금은 송금 편의를 위해 100원 단위로 받는다. */
    private int ceilTo100(double won) {
        return (int) (Math.ceil(won / 100.0) * 100);
    }
}
