package com.yongyong.owlly.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.settlement.dto.FareInput;
import com.yongyong.owlly.domain.settlement.dto.FarePreview;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFareServiceTest {

    @Mock SettlementRepository settlements;
    @Mock SettlementItemRepository settlementItems;
    @Mock TransferRepository transfers;
    @Mock RidePartyRepository rideParties;
    @Mock PartyStopRepository partyStops;
    @Mock SettlementService settlementService;
    @Spy FareShareCalculator fareShareCalculator;
    @InjectMocks SettlementFareService service;

    private static final Long PARTY_ID = 1L;
    private static final Long REP = 30L; // 막차 = 대표

    @Test
    void 실제요금으로_분담_재계산하고_차액을_돌려준다() {
        // 화면 S-06d: 30,000원, 김*연(8km·선송금5400)·나(12km·선송금9500)·대표(16km)
        givenSettlement(30000, 1, null);
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(repParty()));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1))
                .willReturn(List.of(stop(10L, 8000), stop(20L, 4000), stop(REP, 4000)));
        given(settlementItems.findBySettlementIdOrderByIdAsc(any()))
                .willReturn(List.of(item(10L, 5400), item(20L, 9500), item(REP, 0)));

        FarePreview preview = service.preview(PARTY_ID, REP, new FareInput(30000, 1));

        assertThat(preview.actualTotalWon()).isEqualTo(30000);
        assertThat(preview.canFinalize()).isTrue();
        assertThat(preview.blockingReasons()).isEmpty();
        // 확정 분담(방식 A): 5000 / 8750 / 16250
        assertThat(preview.items()).extracting("actualShareWon").containsExactly(5000, 8750, 16250);
        // 차액 = 선송금 − 확정: 김*연 +400, 나 +750, 대표 0
        assertThat(preview.items()).extracting("refundDueWon").containsExactly(400, 750, 0);
    }

    @Test
    void 음수_차액_추가납부도_확정_가능하다() {
        // 선송금이 확정보다 적은 멤버(20L): 5400 보냈는데 확정 8750 → 차액 −3350(추가 납부)
        givenSettlement(30000, 1, null);
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(repParty()));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1))
                .willReturn(List.of(stop(10L, 8000), stop(20L, 4000), stop(REP, 4000)));
        given(settlementItems.findBySettlementIdOrderByIdAsc(any()))
                .willReturn(List.of(item(10L, 9500), item(20L, 5400), item(REP, 0)));

        FarePreview preview = service.preview(PARTY_ID, REP, new FareInput(30000, 1));

        assertThat(preview.items()).extracting("refundDueWon").containsExactly(4500, -3350, 0);
        assertThat(preview.canFinalize()).isTrue(); // 추가 납부 허용
    }

    @Test
    void 대표가_아니면_403() {
        givenSettlement(30000, 1, null);
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(repParty()));

        assertThatThrownBy(() -> service.preview(PARTY_ID, 99L, new FareInput(30000, 1)))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 실제요금이_예상_1_2배를_넘으면_422() {
        givenSettlement(30000, 1, null); // 상한 = 36000
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(repParty()));

        assertThatThrownBy(() -> service.preview(PARTY_ID, REP, new FareInput(40000, 1)))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FARE_LIMIT_EXCEEDED);
    }

    @Test
    void 경로_버전이_다르면_409() {
        givenSettlement(30000, 1, null);
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(repParty()));

        assertThatThrownBy(() -> service.preview(PARTY_ID, REP, new FareInput(30000, 2)))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ROUTE_VERSION_CONFLICT);
    }

    @Test
    void 이미_확정된_정산이면_409() {
        givenSettlement(30000, 1, Instant.now());

        assertThatThrownBy(() -> service.preview(PARTY_ID, REP, new FareInput(30000, 1)))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SETTLEMENT_FINALIZED);
    }

    @Test
    void 확정하면_환급_PENDING_이체를_만들고_정산을_확정한다() {
        Settlement settlement = Settlement.builder().partyId(PARTY_ID).estimatedTotalWon(30000)
                .routeVersion(1).status(SettlementStatus.ESTIMATED).build(); // 미확정
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(settlement));
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(40L)));
        SettlementResponse response = mock(SettlementResponse.class);
        given(settlementService.getSettlement(PARTY_ID, 40L)).willReturn(response);
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1))
                .willReturn(List.of(stop(10L, 8000), stop(30L, 4000), stop(40L, 4000))); // 40 = 대표
        SettlementItem i10 = itemId(1L, 10L, 5400);
        SettlementItem i30 = itemId(2L, 30L, 9500);
        SettlementItem i40 = itemId(3L, 40L, 0);
        given(settlementItems.findBySettlementIdOrderByIdAsc(any())).willReturn(List.of(i10, i30, i40));
        given(transfers.findBySettlementItemIdInOrderByIdAsc(any())).willReturn(List.of(
                prepay(1L, TransferStatus.RECEIVED), prepay(2L, TransferStatus.RECEIVED)));

        SettlementResponse result = service.finalizeSettlement(PARTY_ID, 40L, new FareInput(30000, 1), null);

        assertThat(result).isSameAs(response);
        // 확정 분담 5000/8750/16250 → 환급 10:+400, 30:+750 (양수만 PENDING REFUND)
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transfers, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(t -> {
            assertThat(t.getKind()).isEqualTo(TransferKind.REFUND);
            assertThat(t.getPayerMemberId()).isEqualTo(40L);
            assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDING);
        });
        assertThat(captor.getAllValues()).extracting(Transfer::getAmountWon).containsExactlyInAnyOrder(400, 750);
        assertThat(i10.getActualShareWon()).isEqualTo(5000);
        assertThat(i10.getRefundDueWon()).isEqualTo(400);
        assertThat(i40.getRefundDueWon()).isEqualTo(0);
        assertThat(settlement.getActualTotalWon()).isEqualTo(30000);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REFUNDING);
        assertThat(settlement.getFinalizedAt()).isNotNull();
    }

    @Test
    void 선송금_수령이_안_끝났으면_409_확정_불가() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(Settlement.builder()
                .partyId(PARTY_ID).estimatedTotalWon(30000).routeVersion(1)
                .status(SettlementStatus.ESTIMATED).build()));
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(40L)));
        given(settlementService.getSettlement(PARTY_ID, 40L)).willReturn(mock(SettlementResponse.class));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1))
                .willReturn(List.of(stop(10L, 8000), stop(30L, 4000), stop(40L, 4000)));
        given(settlementItems.findBySettlementIdOrderByIdAsc(any()))
                .willReturn(List.of(itemId(1L, 10L, 5400), itemId(2L, 30L, 9500), itemId(3L, 40L, 0)));
        given(transfers.findBySettlementItemIdInOrderByIdAsc(any())).willReturn(List.of(
                prepay(1L, TransferStatus.PENDING), prepay(2L, TransferStatus.RECEIVED))); // 10번 미수령

        assertThatThrownBy(() -> service.finalizeSettlement(PARTY_ID, 40L, new FareInput(30000, 1), null))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PREPAY_NOT_COMPLETED);
        verify(transfers, never()).save(any());
    }

    @Test
    void 이미_확정된_정산은_멱등으로_현재_상태를_돌려준다() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(Settlement.builder()
                .partyId(PARTY_ID).estimatedTotalWon(30000).routeVersion(1)
                .status(SettlementStatus.REFUNDING).finalizedAt(Instant.now()).build()));
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(40L)));
        SettlementResponse response = mock(SettlementResponse.class);
        given(settlementService.getSettlement(PARTY_ID, 40L)).willReturn(response);

        SettlementResponse result = service.finalizeSettlement(PARTY_ID, 40L, new FareInput(30000, 1), null);

        assertThat(result).isSameAs(response);
        verify(transfers, never()).save(any());
    }

    private RideParty party(Long representativeMemberId) {
        return RideParty.builder().representativeMemberId(representativeMemberId).routeVersion(1).build();
    }

    private SettlementItem itemId(Long id, Long memberId, int prepayDueWon) {
        return SettlementItem.builder().id(id).memberId(memberId)
                .estimatedShareWon(prepayDueWon).prepayDueWon(prepayDueWon)
                .status(SettlementItemStatus.ESTIMATED).build();
    }

    private Transfer prepay(Long settlementItemId, TransferStatus status) {
        return Transfer.builder().settlementItemId(settlementItemId).kind(TransferKind.PREPAY)
                .payerMemberId(0L).payeeMemberId(0L).amountWon(0).status(status).build();
    }

    private void givenSettlement(int estimatedTotalWon, int routeVersion, Instant finalizedAt) {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(Settlement.builder()
                .partyId(PARTY_ID).estimatedTotalWon(estimatedTotalWon).routeVersion(routeVersion)
                .status(SettlementStatus.ESTIMATED).finalizedAt(finalizedAt).build()));
    }

    private RideParty repParty() {
        return RideParty.builder().representativeMemberId(REP).routeVersion(1).build();
    }

    private PartyStop stop(Long memberId, int segmentDistanceM) {
        return PartyStop.builder().partyId(PARTY_ID).memberId(memberId)
                .routeVersion(1).segmentDistanceM(segmentDistanceM).build();
    }

    private SettlementItem item(Long memberId, int prepayDueWon) {
        return SettlementItem.builder().memberId(memberId).prepayDueWon(prepayDueWon).build();
    }
}
