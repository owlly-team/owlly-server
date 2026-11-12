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
            return settlementItems.findBySettlementIdOrderByIdAsc(existing.get().getId()).stream()
                    .map(item -> new MemberEstimate(item.getMemberId(), item.getEstimatedShareWon(), item.getPrepayDueWon()))
                    .toList();
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
        int[] shares = computeShares(stops, estimatedTotalWon);

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
     * 거리 비례(구간별 인원 분할)로 멤버별 분담액을 계산한다. 합계는 전체 요금과 정확히 일치시킨다.
     *
     * <p>하차 순서대로 stops[0..n-1]. 구간 k(0-based)는 stops[k] 가 내리는 구간이고, 그 구간엔 아직 내리지 않은
     * (n-k)명이 타고 있다. 구간 요금은 거리 비례(total × d_k / D)이고 그 인원수로 균등 분할한다.
     * stops[j] 는 구간 0..j 를 타므로 그 합이 분담액. 반올림 잔차는 대표(막차)가 흡수한다.
     */
    private int[] computeShares(List<PartyStop> stops, int total) {
        int n = stops.size();
        int[] shares = new int[n];

        long totalDistance = 0;
        for (PartyStop stop : stops) {
            totalDistance += stop.getSegmentDistanceM();
        }
        if (totalDistance <= 0) { // 거리 정보가 없으면 균등 분할로 대체
            int base = total / n;
            for (int i = 0; i < n; i++) {
                shares[i] = base;
            }
            shares[n - 1] += total - base * n;
            return shares;
        }

        long rounded = 0;
        for (int j = 0; j < n; j++) {
            double share = 0;
            for (int k = 0; k <= j; k++) {
                double segmentFare = (double) total * stops.get(k).getSegmentDistanceM() / totalDistance;
                share += segmentFare / (n - k); // 구간 k 에 탄 인원 = n - k
            }
            shares[j] = (int) Math.round(share);
            rounded += shares[j];
        }
        shares[n - 1] += (int) (total - rounded); // 잔차는 대표가 흡수 → 합계 정확히 일치
        return shares;
    }

    /** 100원 단위 올림. 선송금은 송금 편의를 위해 100원 단위로 받는다. */
    private int ceilTo100(double won) {
        return (int) (Math.ceil(won / 100.0) * 100);
    }
}
