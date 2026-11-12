package com.yongyong.owlly.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.party.dto.PartyMemberRow;
import com.yongyong.owlly.domain.party.entity.BoardingStatus;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.party.service.EncryptedValueReader;
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
import com.yongyong.owlly.domain.user.entity.Gender;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정산 조회(API-33 getSettlement)의 조립 규칙. 저장소·복호화는 가짜로 둔다.
 *
 * <p>상황: S-06 정산 내역·멤버 화면. 대표(503)와 일반 멤버(501·502)로 이뤄진 팟을
 * 멤버 501 시점으로 조회한다. 전원 항목이 배열로 오고, 각 항목에 이름·누적 거리·대표/본인
 * 여부가 채워지는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock SettlementRepository settlements;
    @Mock SettlementItemRepository settlementItems;
    @Mock TransferRepository transfers;
    @Mock PartyMemberRepository partyMembers;
    @Mock PartyStopRepository partyStops;
    @Mock RidePartyRepository rideParties;
    @Mock EncryptedValueReader encryptedValueReader;
    @InjectMocks SettlementService service;

    private static final Long PARTY_ID = 1L;
    private static final int ROUTE_VERSION = 2;
    // 멤버: 501 김소연(조회자), 502 한서윤, 503 이정준(대표·막차)
    private static final Long VIEWER = 501L;

    @Test
    void 전원_항목을_배열로_주고_이름_누적거리_대표_본인_여부를_채운다() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(settlement()));
        given(settlementItems.findBySettlementIdOrderByIdAsc(901L)).willReturn(List.of(
                item(10001L, 501L, 5000, 6000, 5000, 1000, SettlementItemStatus.REFUND_PENDING),
                item(10002L, 502L, 8750, 10500, 8750, 1750, SettlementItemStatus.REFUND_PENDING),
                item(10003L, 503L, 16250, 0, 16250, 0, SettlementItemStatus.COMPLETED)));
        // 501 항목에 PREPAY+REFUND 2건, 502 항목에 PREPAY 1건, 503(대표) 항목엔 이체 없음
        given(transfers.findBySettlementItemIdInOrderByIdAsc(any())).willReturn(List.of(
                transfer(801L, 10001L, TransferKind.PREPAY, 501L, 503L, 6000, TransferStatus.RECEIVED),
                transfer(802L, 10001L, TransferKind.REFUND, 503L, 501L, 1000, TransferStatus.PENDING),
                transfer(803L, 10002L, TransferKind.PREPAY, 502L, 503L, 10500, TransferStatus.RECEIVED)));
        given(partyMembers.findRowsByPartyId(PARTY_ID)).willReturn(List.of(
                row(501L, "ENC_501"), row(502L, "ENC_502"), row(503L, "ENC_503")));
        given(encryptedValueReader.readText("ENC_501")).willReturn("김소연");
        given(encryptedValueReader.readText("ENC_502")).willReturn("한서윤");
        given(encryptedValueReader.readText("ENC_503")).willReturn("이정준");
        // 구간 거리 8·4·4km → 누적 8·12·16km (503이 막차=대표)
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, ROUTE_VERSION))
                .willReturn(List.of(stop(501L, 1, 8000), stop(502L, 2, 4000), stop(503L, 3, 4000)));
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(partyWithRepresentative(503L)));

        SettlementResponse res = service.getSettlement(PARTY_ID, VIEWER);

        assertThat(res.partyId()).isEqualTo(PARTY_ID);
        assertThat(res.estimatedTotalWon()).isEqualTo(30000);
        assertThat(res.items())
                .extracting(SettlementResponse.Item::memberId,
                        SettlementResponse.Item::maskedName,
                        SettlementResponse.Item::distanceMeters,
                        SettlementResponse.Item::isRepresentative,
                        SettlementResponse.Item::isMe)
                .containsExactly(
                        tuple(501L, "김*연", 8000, false, true),   // 조회자 본인
                        tuple(502L, "한*윤", 12000, false, false),
                        tuple(503L, "이*준", 16000, true, false)); // 대표·막차
    }

    @Test
    void 송금은_항목별로_묶이고_이체없는_항목은_빈_목록이다() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.of(settlement()));
        given(settlementItems.findBySettlementIdOrderByIdAsc(901L)).willReturn(List.of(
                item(10001L, 501L, 5000, 6000, 5000, 1000, SettlementItemStatus.REFUND_PENDING),
                item(10002L, 502L, 8750, 10500, 8750, 1750, SettlementItemStatus.REFUND_PENDING),
                item(10003L, 503L, 16250, 0, 16250, 0, SettlementItemStatus.COMPLETED)));
        given(transfers.findBySettlementItemIdInOrderByIdAsc(any())).willReturn(List.of(
                transfer(801L, 10001L, TransferKind.PREPAY, 501L, 503L, 6000, TransferStatus.RECEIVED),
                transfer(802L, 10001L, TransferKind.REFUND, 503L, 501L, 1000, TransferStatus.PENDING),
                transfer(803L, 10002L, TransferKind.PREPAY, 502L, 503L, 10500, TransferStatus.RECEIVED)));
        given(partyMembers.findRowsByPartyId(PARTY_ID)).willReturn(List.of(
                row(501L, "ENC_501"), row(502L, "ENC_502"), row(503L, "ENC_503")));
        given(encryptedValueReader.readText(any())).willReturn("홍길동");
        given(partyStops.findByPartyIdAndRouteVersionOrderByStopOrderAsc(PARTY_ID, ROUTE_VERSION))
                .willReturn(List.of(stop(501L, 1, 8000), stop(502L, 2, 4000), stop(503L, 3, 4000)));
        given(rideParties.findById(PARTY_ID)).willReturn(Optional.of(partyWithRepresentative(503L)));

        SettlementResponse res = service.getSettlement(PARTY_ID, VIEWER);

        assertThat(res.items().get(0).transfers()).hasSize(2);  // 501: PREPAY+REFUND
        assertThat(res.items().get(1).transfers()).hasSize(1);  // 502: PREPAY
        assertThat(res.items().get(2).transfers()).isEmpty();   // 503 대표: 없음
        assertThat(res.items().get(0).transfers())
                .extracting(t -> t.kind(), t -> t.amountWon())
                .containsExactly(tuple("PREPAY", 6000), tuple("REFUND", 1000));
    }

    @Test
    void 정산이_아직_없으면_SETTLEMENT_NOT_READY() {
        given(settlements.findByPartyId(PARTY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSettlement(PARTY_ID, VIEWER))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_READY);
    }

    // --- 빌더 헬퍼 ---

    private static Settlement settlement() {
        return Settlement.builder()
                .id(901L)
                .partyId(PARTY_ID)
                .estimatedTotalWon(30000)
                .actualTotalWon(30000)
                .routeVersion(ROUTE_VERSION)
                .status(SettlementStatus.REFUNDING)
                .build();
    }

    private static SettlementItem item(Long id, Long memberId, int estimated, int prepay,
            Integer actual, Integer refund, SettlementItemStatus status) {
        return SettlementItem.builder()
                .id(id)
                .settlementId(901L)
                .memberId(memberId)
                .estimatedShareWon(estimated)
                .prepayDueWon(prepay)
                .actualShareWon(actual)
                .refundDueWon(refund)
                .status(status)
                .build();
    }

    private static Transfer transfer(Long id, Long itemId, TransferKind kind, Long payer, Long payee,
            int amount, TransferStatus status) {
        return Transfer.builder()
                .id(id)
                .settlementItemId(itemId)
                .kind(kind)
                .payerMemberId(payer)
                .payeeMemberId(payee)
                .amountWon(amount)
                .status(status)
                .build();
    }

    private static PartyMemberRow row(Long memberId, String realNameEncrypted) {
        return new PartyMemberRow(memberId, memberId, MemberDecision.ACCEPTED, BoardingStatus.BOARDED,
                Gender.FEMALE, realNameEncrypted, "강남대학교", "영통동");
    }

    private static PartyStop stop(Long memberId, int stopOrder, int segmentDistanceM) {
        return PartyStop.builder()
                .partyId(PARTY_ID)
                .memberId(memberId)
                .routeVersion(ROUTE_VERSION)
                .stopOrder(stopOrder)
                .segmentDistanceM(segmentDistanceM)
                .routingSource("TEST")
                .build();
    }

    private static RideParty partyWithRepresentative(Long representativeMemberId) {
        return RideParty.builder()
                .representativeMemberId(representativeMemberId)
                .routeVersion(ROUTE_VERSION)
                .build();
    }
}
