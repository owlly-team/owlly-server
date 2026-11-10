package com.yongyong.owlly.domain.party.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.party.dto.PartyAction;
import com.yongyong.owlly.domain.party.entity.BoardingStatus;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.party.entity.PartyStatus;
import com.yongyong.owlly.domain.party.entity.RideParty;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class PartyServiceTest {

    private static final Instant DEPARTURE = Instant.parse("2026-11-10T14:30:00Z");

    private static RideParty party(PartyStatus status, Instant confirmDeadlineAt) {
        return RideParty.builder()
                .departureAt(DEPARTURE)
                .confirmDeadlineAt(confirmDeadlineAt)
                .status(status)
                .build();
    }

    private static Instant minutesBeforeDeparture(long minutes) {
        return DEPARTURE.minus(minutes, ChronoUnit.MINUTES);
    }

    @Test
    void 확정_마감_전이고_결정_전이면_확정하기와_빠지기() {
        RideParty party = party(PartyStatus.CONFIRMING, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, BoardingStatus.UNKNOWN, minutesBeforeDeparture(20), false))
                .containsExactly(PartyAction.CONFIRM, PartyAction.LEAVE);
    }

    @Test
    void 막차_기준_확정_마감이_지나면_확정하기는_빠지고_빠지기만_남는다() {
        RideParty party = party(PartyStatus.CONFIRMING, minutesBeforeDeparture(40));

        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, BoardingStatus.UNKNOWN, minutesBeforeDeparture(30), false))
                .containsExactly(PartyAction.LEAVE);
    }

    @Test
    void 확정_마감이_비어_있으면_출발_15분_전까지_확정할_수_있다() {
        RideParty party = party(PartyStatus.CONFIRMING, null);

        assertThat(PartyService.confirmDeadline(party)).isEqualTo(minutesBeforeDeparture(15));
        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, BoardingStatus.UNKNOWN, minutesBeforeDeparture(15), false))
                .contains(PartyAction.CONFIRM);
        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, BoardingStatus.UNKNOWN, minutesBeforeDeparture(14), false))
                .doesNotContain(PartyAction.CONFIRM);
    }

    @Test
    void 이미_확정했으면_빠지기만() {
        RideParty party = party(PartyStatus.CONFIRMING, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(20), false))
                .containsExactly(PartyAction.LEAVE);
    }

    @Test
    void 출발_15분_전이_지나면_빠지기는_없고_탑승_기록만() {
        RideParty party = party(PartyStatus.READY, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(14), false))
                .containsExactly(PartyAction.RECORD_BOARDING);
    }

    @Test
    void 탑승_기록은_확정한_멤버가_출발_1시간_뒤까지_한_번() {
        RideParty party = party(PartyStatus.RIDING, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(-60), false))
                .containsExactly(PartyAction.RECORD_BOARDING);
        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(-61), false))
                .isEmpty();
        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, BoardingStatus.BOARDED, minutesBeforeDeparture(-10), false))
                .isEmpty();
        assertThat(PartyService.allowedActions(party(PartyStatus.PREPAYING, minutesBeforeDeparture(15)),
                MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(16), false))
                .containsExactly(PartyAction.LEAVE);
    }

    @Test
    void 이미_빠졌거나_해산된_팟이면_아무_버튼도_없다() {
        assertThat(PartyService.allowedActions(
                party(PartyStatus.CONFIRMING, minutesBeforeDeparture(15)), MemberDecision.LEFT, BoardingStatus.UNKNOWN, minutesBeforeDeparture(20), false))
                .isEmpty();
        assertThat(PartyService.allowedActions(
                party(PartyStatus.CANCELLED, minutesBeforeDeparture(15)), MemberDecision.PENDING, BoardingStatus.UNKNOWN, minutesBeforeDeparture(20), false))
                .isEmpty();
    }

    @Test
    void 택시_호출_확인은_선송금_단계의_대표가_한_번() {
        RideParty prepaying = party(PartyStatus.PREPAYING, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(prepaying, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(20), true))
                .containsExactly(PartyAction.LEAVE, PartyAction.CONFIRM_TAXI_CALL);
        assertThat(PartyService.allowedActions(prepaying, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(20), false))
                .containsExactly(PartyAction.LEAVE);

        RideParty called = RideParty.builder()
                .departureAt(DEPARTURE)
                .taxiCalledAt(minutesBeforeDeparture(5))
                .status(PartyStatus.READY)
                .build();
        assertThat(PartyService.allowedActions(called, MemberDecision.ACCEPTED, BoardingStatus.UNKNOWN, minutesBeforeDeparture(5), true))
                .containsExactly(PartyAction.RECORD_BOARDING);
    }
}
