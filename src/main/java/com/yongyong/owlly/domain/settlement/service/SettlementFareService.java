package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.settlement.dto.FareInput;
import com.yongyong.owlly.domain.settlement.dto.FarePreview;
import com.yongyong.owlly.domain.settlement.dto.FarePreviewItem;
import com.yongyong.owlly.domain.settlement.dto.SettlementResponse;
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
import java.time.Duration;
import java.time.Instant;
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

    /** 환급 기한: 확정 후 7일. 기산점 정책은 확정 전까지 가정값. */
    private static final Duration REFUND_DUE_AFTER_FINALIZE = Duration.ofDays(7);

    private final SettlementRepository settlements;
    private final SettlementItemRepository settlementItems;
    private final TransferRepository transfers;
    private final RidePartyRepository rideParties;
    private final PartyStopRepository partyStops;
    private final FareShareCalculator fareShareCalculator;
    private final SettlementService settlementService;

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
        Map<Long, Integer> prepayByMemberId = prepayByMemberId(context.items());

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
     * 실제 요금으로 정산을 확정한다 (API-38). 미리보기를 신뢰하지 않고 다시 계산해 저장한다.
     *
     * <ul>
     *   <li>이미 확정됐으면 현재 상태를 그대로 돌려준다(멱등).</li>
     *   <li>선송금 수령이 아직 안 끝났으면 409 PREPAY_NOT_COMPLETED.</li>
     *   <li>양수 차액은 PENDING REFUND 이체(대표→멤버)로 만들고, 음수(추가 납부)는 항목에 기록만 한다.</li>
     * </ul>
     *
     * @param ifMatch 확정 직전 조회한 정산 ETag. 그 사이 상태가 바뀌었으면 412
     * @return 확정 후 정산 상태
     */
    @Transactional
    public SettlementResponse finalizeSettlement(Long partyId, Long callerMemberId, FareInput input, String ifMatch) {
        Settlement settlement = settlements.findByPartyId(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.SETTLEMENT_NOT_READY));
        RideParty party = rideParties.findById(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(party.getRepresentativeMemberId(), callerMemberId)) {
            throw new OwllyException(ErrorCode.FORBIDDEN); // 대표만 확정 가능
        }

        SettlementResponse current = settlementService.getSettlement(partyId, callerMemberId);
        if (ifMatch != null && !ETags.matches(ifMatch, current)) {
            throw new OwllyException(ErrorCode.PRECONDITION_FAILED);
        }
        if (settlement.getFinalizedAt() != null) {
            return current; // 이미 확정 → 멱등
        }

        Context context = loadForActualFare(partyId, callerMemberId, input);
        Map<Long, List<Transfer>> transfersByItemId = transfersByItemId(context.items());
        requireAllPrepayReceived(context.items(), transfersByItemId, context.representativeMemberId());

        int[] shares = fareShareCalculator.shares(context.stops(), input.actualTotalWon());
        Map<Long, Integer> prepayByMemberId = prepayByMemberId(context.items());
        Map<Long, SettlementItem> itemByMemberId = context.items().stream()
                .collect(Collectors.toMap(SettlementItem::getMemberId, item -> item));

        boolean anyDiffPending = false;
        for (int i = 0; i < context.stops().size(); i++) {
            Long memberId = context.stops().get(i).getMemberId();
            SettlementItem item = itemByMemberId.get(memberId);
            int actualShare = shares[i];

            if (memberId.equals(context.representativeMemberId())) {
                item.finalizeFare(actualShare, 0, SettlementItemStatus.COMPLETED); // 대표는 환급 없음
                continue;
            }
            int refundDue = prepayByMemberId.getOrDefault(memberId, 0) - actualShare;
            if (refundDue > 0) { // 환급: 대표 → 멤버 PENDING REFUND 이체
                transfers.save(Transfer.builder()
                        .settlementItemId(item.getId())
                        .kind(TransferKind.REFUND)
                        .payerMemberId(context.representativeMemberId())
                        .payeeMemberId(memberId)
                        .amountWon(refundDue)
                        .status(TransferStatus.PENDING)
                        .build());
            }
            // 음수(추가 납부)는 이체를 만들지 않고 항목의 음수 차액으로만 남긴다(멤버가 수동 송금).
            SettlementItemStatus status = refundDue == 0
                    ? SettlementItemStatus.COMPLETED : SettlementItemStatus.REFUND_PENDING;
            item.finalizeFare(actualShare, refundDue, status);
            anyDiffPending |= refundDue != 0;
        }

        Instant now = Instant.now();
        SettlementStatus status = anyDiffPending ? SettlementStatus.REFUNDING : SettlementStatus.COMPLETED;
        Instant refundDueAt = anyDiffPending ? now.plus(REFUND_DUE_AFTER_FINALIZE) : null;
        settlement.finalizeFare(input.actualTotalWon(), status, now, refundDueAt);

        return settlementService.getSettlement(partyId, callerMemberId);
    }

    /** 대표를 뺀 모든 멤버의 선송금이 수령 확인(RECEIVED)됐는지. 아직이면 확정 불가. */
    private void requireAllPrepayReceived(List<SettlementItem> items, Map<Long, List<Transfer>> transfersByItemId,
                                          Long representativeMemberId) {
        boolean anyPending = items.stream()
                .filter(item -> !item.getMemberId().equals(representativeMemberId))
                .flatMap(item -> transfersByItemId.getOrDefault(item.getId(), List.of()).stream())
                .anyMatch(t -> t.getKind() == TransferKind.PREPAY
                        && (t.getStatus() == TransferStatus.PENDING || t.getStatus() == TransferStatus.SENT));
        if (anyPending) {
            throw new OwllyException(ErrorCode.PREPAY_NOT_COMPLETED);
        }
    }

    private Map<Long, Integer> prepayByMemberId(List<SettlementItem> items) {
        return items.stream().collect(Collectors.toMap(SettlementItem::getMemberId, SettlementItem::getPrepayDueWon));
    }

    private Map<Long, List<Transfer>> transfersByItemId(List<SettlementItem> items) {
        return transfers.findBySettlementItemIdInOrderByIdAsc(items.stream().map(SettlementItem::getId).toList())
                .stream().collect(Collectors.groupingBy(Transfer::getSettlementItemId));
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
