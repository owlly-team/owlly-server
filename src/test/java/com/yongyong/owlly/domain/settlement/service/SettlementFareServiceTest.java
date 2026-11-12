package com.yongyong.owlly.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.settlement.dto.FareInput;
import com.yongyong.owlly.domain.settlement.dto.FarePreview;
import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import com.yongyong.owlly.domain.settlement.entity.SettlementStatus;
import com.yongyong.owlly.domain.settlement.repository.SettlementItemRepository;
import com.yongyong.owlly.domain.settlement.repository.SettlementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFareServiceTest {

    @Mock SettlementRepository settlements;
    @Mock SettlementItemRepository settlementItems;
    @Mock RidePartyRepository rideParties;
    @Mock PartyStopRepository partyStops;
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
