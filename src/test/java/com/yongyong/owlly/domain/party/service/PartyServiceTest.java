package com.yongyong.owlly.domain.party.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.party.dto.PartyAction;
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

        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, minutesBeforeDeparture(20)))
                .containsExactly(PartyAction.CONFIRM, PartyAction.LEAVE);
    }

    @Test
    void 막차_기준_확정_마감이_지나면_확정하기는_빠지고_빠지기만_남는다() {
        RideParty party = party(PartyStatus.CONFIRMING, minutesBeforeDeparture(40));

        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, minutesBeforeDeparture(30)))
                .containsExactly(PartyAction.LEAVE);
    }

    @Test
    void 확정_마감이_비어_있으면_출발_15분_전까지_확정할_수_있다() {
        RideParty party = party(PartyStatus.CONFIRMING, null);

        assertThat(PartyService.confirmDeadline(party)).isEqualTo(minutesBeforeDeparture(15));
        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, minutesBeforeDeparture(15)))
                .contains(PartyAction.CONFIRM);
        assertThat(PartyService.allowedActions(party, MemberDecision.PENDING, minutesBeforeDeparture(14)))
                .doesNotContain(PartyAction.CONFIRM);
    }

    @Test
    void 이미_확정했으면_빠지기만() {
        RideParty party = party(PartyStatus.CONFIRMING, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, minutesBeforeDeparture(20)))
                .containsExactly(PartyAction.LEAVE);
    }

    @Test
    void 출발_15분_전이_지나면_아무_버튼도_없다() {
        RideParty party = party(PartyStatus.READY, minutesBeforeDeparture(15));

        assertThat(PartyService.allowedActions(party, MemberDecision.ACCEPTED, minutesBeforeDeparture(14)))
                .isEmpty();
    }

    @Test
    void 이미_빠졌거나_해산된_팟이면_아무_버튼도_없다() {
        assertThat(PartyService.allowedActions(
                party(PartyStatus.CONFIRMING, minutesBeforeDeparture(15)), MemberDecision.LEFT, minutesBeforeDeparture(20)))
                .isEmpty();
        assertThat(PartyService.allowedActions(
                party(PartyStatus.CANCELLED, minutesBeforeDeparture(15)), MemberDecision.PENDING, minutesBeforeDeparture(20)))
                .isEmpty();
    }
}
