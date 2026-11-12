package com.yongyong.owlly.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
class SettlementEstimateServiceTest {

    @Mock RidePartyRepository rideParties;
    @Mock PartyStopRepository partyStops;
    @Mock SettlementRepository settlements;
    @Mock SettlementItemRepository settlementItems;
    @Mock TransferRepository transfers;
    @Spy FareShareCalculator fareShareCalculator;
    @InjectMocks SettlementEstimateService service;

    private static final Long PARTY_ID = 1L;

    @Test
    void 거리비례로_분담하고_선송금은_120퍼센트_올림_대표는_0() {
        // given: 4명, 전체 20,000원, 구간 5·3·2·2km (멤버 10→20→30→40 순서로 하차, 40이 막차=대표)
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(1)));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1))
                .willReturn(List.of(stop(10L, 5000), stop(20L, 3000), stop(30L, 2000), stop(40L, 2000)));
        given(settlements.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(settlementItems.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        List<MemberEstimate> result = service.createEstimate(PARTY_ID, 20000);

        // then: 방식 A 분담액 — 먼저 내릴수록 적게, 합계는 정확히 전체요금
        assertThat(result).extracting(MemberEstimate::estimatedShareWon)
                .containsExactly(2083, 3750, 5417, 8750);
        assertThat(result.stream().mapToInt(MemberEstimate::estimatedShareWon).sum()).isEqualTo(20000);
        // 선송금 = 분담액×1.2 올림(100원), 대표(막차=40)는 0
        assertThat(result).extracting(MemberEstimate::prepayDueWon)
                .containsExactly(2500, 4500, 6600, 0);
    }

    @Test
    void 정산_항목_PENDING_선송금을_저장한다() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(1)));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1))
                .willReturn(List.of(stop(10L, 5000), stop(20L, 3000), stop(40L, 2000)));
        given(settlements.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(settlementItems.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.createEstimate(PARTY_ID, 15000);

        // 정산 1건, 개인 항목 3건(전원), 선송금 이체 2건(대표 제외)
        verify(settlements, times(1)).save(any());
        ArgumentCaptor<SettlementItem> itemCaptor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItems, times(3)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).allSatisfy(item ->
                assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.ESTIMATED));

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transfers, times(2)).save(transferCaptor.capture());
        // 모든 선송금은 대표(막차=40)에게 간다
        assertThat(transferCaptor.getAllValues()).allSatisfy(t ->
                assertThat(t.getPayeeMemberId()).isEqualTo(40L));
    }

    @Test
    void 이미_정산이_있으면_기존_항목을_돌려주고_새로_만들지_않는다() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(Settlement.builder().build()));
        given(settlementItems.findBySettlementIdOrderByIdAsc(any())).willReturn(List.of(
                SettlementItem.builder().memberId(10L).estimatedShareWon(5000).prepayDueWon(6000).build()));

        List<MemberEstimate> result = service.createEstimate(PARTY_ID, 20000);

        assertThat(result).containsExactly(new MemberEstimate(10L, 5000, 6000));
        verify(rideParties, never()).findById(any());
        verify(settlements, never()).save(any());
    }

    @Test
    void 하차_순서가_없으면_빈_목록을_돌려준다() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(1)));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 1)).willReturn(List.of());

        assertThat(service.createEstimate(PARTY_ID, 20000)).isEmpty();
        verify(settlements, never()).save(any());
    }

    @Test
    void 이탈_후_재계산_빠진_멤버_정리하고_기수령_선송금은_보존한다() {
        // 멤버 10·20·30·40 중 20 이탈 → 남은 10·30·40(대표) 새 경로(rv2)로 재계산, 전체 20,000
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(Settlement.builder()
                .partyId(PARTY_ID).estimatedTotalWon(28000).routeVersion(1)
                .status(SettlementStatus.ESTIMATED).build()));
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(party(2)));
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, 2))
                .willReturn(List.of(stop(10L, 8000), stop(30L, 4000), stop(40L, 4000))); // 40 = 대표(막차)
        SettlementItem item10 = item(1L, 10L, 5000);  // 아직 안 보낸 선송금
        SettlementItem item20 = item(2L, 20L, 6000);  // 이탈자
        SettlementItem item30 = item(3L, 30L, 7000);  // 이미 받은 선송금 → 보존
        SettlementItem item40 = item(4L, 40L, 0);     // 대표
        given(settlementItems.findBySettlementIdOrderByIdAsc(any()))
                .willReturn(List.of(item10, item20, item30, item40));
        Transfer t10 = prepay(1L, TransferStatus.PENDING, 5000);
        Transfer t20 = prepay(2L, TransferStatus.PENDING, 6000);
        Transfer t30 = prepay(3L, TransferStatus.RECEIVED, 7000);
        given(transfers.findBySettlementItemIdInOrderByIdAsc(any())).willReturn(List.of(t10, t20, t30));

        List<MemberEstimate> result = service.reEstimate(PARTY_ID, 20000);

        // 이탈자(20) 제외, 남은 3명만
        assertThat(result).extracting(MemberEstimate::memberId).containsExactly(10L, 30L, 40L);
        // 이탈자 항목 삭제 + PENDING 선송금 취소
        verify(settlementItems).delete(item20);
        assertThat(t20.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        // 10: 새 분담 3333 → 선송금 ceil100(3333×1.2)=4000, PENDING 이체 금액도 갱신
        assertThat(result).filteredOn(e -> e.memberId() == 10L)
                .singleElement().extracting(MemberEstimate::prepayDueWon).isEqualTo(4000);
        assertThat(t10.getAmountWon()).isEqualTo(4000);
        // 30: 이미 받은 선송금 7000 보존(이체 금액 그대로)
        assertThat(result).filteredOn(e -> e.memberId() == 30L)
                .singleElement().extracting(MemberEstimate::prepayDueWon).isEqualTo(7000);
        assertThat(t30.getAmountWon()).isEqualTo(7000);
        // 40: 대표 선송금 0
        assertThat(result).filteredOn(e -> e.memberId() == 40L)
                .singleElement().extracting(MemberEstimate::prepayDueWon).isEqualTo(0);
    }

    @Test
    void 정산이_없으면_재계산은_빈_목록() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        assertThat(service.reEstimate(PARTY_ID, 20000)).isEmpty();
        verify(rideParties, never()).findById(any());
    }

    private RideParty party(int routeVersion) {
        return RideParty.builder().routeVersion(routeVersion).build();
    }

    private SettlementItem item(Long id, Long memberId, int prepayDueWon) {
        return SettlementItem.builder().id(id).memberId(memberId)
                .estimatedShareWon(prepayDueWon).prepayDueWon(prepayDueWon)
                .status(SettlementItemStatus.ESTIMATED).build();
    }

    private Transfer prepay(Long settlementItemId, TransferStatus status, int amountWon) {
        return Transfer.builder().settlementItemId(settlementItemId).kind(TransferKind.PREPAY)
                .payerMemberId(0L).payeeMemberId(0L).amountWon(amountWon).status(status).build();
    }

    private PartyStop stop(Long memberId, int segmentDistanceM) {
        return PartyStop.builder().partyId(PARTY_ID).memberId(memberId)
                .routeVersion(1).segmentDistanceM(segmentDistanceM).build();
    }
}
