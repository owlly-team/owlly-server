package com.yongyong.owlly.domain.journey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyChoiceMode;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.OutcomeSource;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 막차·혼자 택시 선택 기록(API-17)의 업무 규칙. 저장소는 가짜로 둔다. */
class JourneyChoiceServiceTest {

    private static final long ME = 1L;
    private static final long JOURNEY_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-11T14:00:00Z");

    private final JourneyRepository journeyRepository = mock(JourneyRepository.class);
    private final RideRequestRepository rideRequestRepository = mock(RideRequestRepository.class);
    private final PlaceNameReader placeNameReader = mock(PlaceNameReader.class);
    private final JourneyService service =
            new JourneyService(journeyRepository, rideRequestRepository, placeNameReader);

    private Journey journey(long userId, JourneyOutcome outcome) {
        Journey journey = Journey.builder()
                .id(JOURNEY_ID).userId(userId).universityId(1L).pickupPointId(1L).destinationDongId(1L)
                .intendedDepartureAt(NOW).outcome(outcome)
                .build();
        when(journeyRepository.findByIdForUpdate(JOURNEY_ID)).thenReturn(Optional.of(journey));
        return journey;
    }

    private RideRequest request(long id, RideRequestStatus status) {
        return RideRequest.builder().id(id).journeyId(JOURNEY_ID).status(status).build();
    }

    private void activeRequests(RideRequest... requests) {
        when(rideRequestRepository.findByJourneyIdAndStatusInForUpdate(anyLong(), anyCollection()))
                .thenReturn(List.of(requests));
    }

    private ErrorCode errorOf(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(OwllyException.class);
        try {
            call.run();
        } catch (OwllyException e) {
            return e.getErrorCode();
        }
        return null;
    }

    @Test
    void 매칭_대기_신청이_있으면_취소하고_막차_선택을_기록한다() {
        Journey journey = journey(ME, JourneyOutcome.UNRESOLVED);
        RideRequest waiting = request(100L, RideRequestStatus.WAITING);
        activeRequests(waiting);

        JourneyResponse response = service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.BUS, NOW);

        assertThat(waiting.getStatus()).isEqualTo(RideRequestStatus.CANCELLED);
        assertThat(journey.getOutcome()).isEqualTo(JourneyOutcome.BUS_SELECTED);
        assertThat(journey.getOutcomeAt()).isEqualTo(NOW);
        assertThat(journey.getOutcomeSource()).isEqualTo(OutcomeSource.USER_CHOICE);
        assertThat(response.outcome()).isEqualTo(JourneyOutcome.BUS_SELECTED);
        assertThat(response.activeRequestId()).isNull();
    }

    @Test
    void 신청이_없어도_혼자_택시_선택을_기록한다() {
        Journey journey = journey(ME, JourneyOutcome.UNRESOLVED);
        activeRequests();

        service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.SOLO, NOW);

        assertThat(journey.getOutcome()).isEqualTo(JourneyOutcome.SOLO_SELECTED);
    }

    @Test
    void 이미_팟에_배정된_신청이_있으면_팟_이탈이_먼저라는_409() {
        Journey journey = journey(ME, JourneyOutcome.UNRESOLVED);
        RideRequest matched = request(100L, RideRequestStatus.MATCHED);
        activeRequests(matched);

        assertThat(errorOf(() -> service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.BUS, NOW)))
                .isEqualTo(ErrorCode.PARTY_LEAVE_REQUIRED);

        assertThat(matched.getStatus()).isEqualTo(RideRequestStatus.MATCHED);
        assertThat(journey.getOutcome()).isEqualTo(JourneyOutcome.UNRESOLVED);
    }

    @Test
    void 같은_선택을_다시_보내면_현재_결과를_돌려주고_다시_기록하지_않는다() {
        Journey journey = journey(ME, JourneyOutcome.BUS_SELECTED);
        journey.chooseByUser(JourneyChoiceMode.BUS, Instant.parse("2026-11-11T13:00:00Z"));

        JourneyResponse response = service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.BUS, NOW);

        assertThat(response.outcome()).isEqualTo(JourneyOutcome.BUS_SELECTED);
        assertThat(response.outcomeAt()).isEqualTo(Instant.parse("2026-11-11T13:00:00Z"));
        verifyNoInteractions(rideRequestRepository);
    }

    @Test
    void 이미_다른_선택이_있으면_덮어쓰지_않고_409() {
        Journey journey = journey(ME, JourneyOutcome.BUS_SELECTED);

        assertThat(errorOf(() -> service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.SOLO, NOW)))
                .isEqualTo(ErrorCode.JOURNEY_ALREADY_RESOLVED);

        assertThat(journey.getOutcome()).isEqualTo(JourneyOutcome.BUS_SELECTED);
    }

    @Test
    void 동승_탑승이_기록된_귀가나_취소된_귀가는_선택할_수_없다() {
        journey(ME, JourneyOutcome.POOL_BOARDED);
        assertThat(errorOf(() -> service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.BUS, NOW)))
                .isEqualTo(ErrorCode.JOURNEY_ALREADY_RESOLVED);

        journey(ME, JourneyOutcome.CANCELLED);
        assertThat(errorOf(() -> service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.SOLO, NOW)))
                .isEqualTo(ErrorCode.JOURNEY_ALREADY_RESOLVED);
    }

    @Test
    void 남의_귀가나_없는_귀가는_똑같이_404() {
        journey(999L, JourneyOutcome.UNRESOLVED);
        assertThat(errorOf(() -> service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.BUS, NOW)))
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        when(journeyRepository.findByIdForUpdate(JOURNEY_ID)).thenReturn(Optional.empty());
        assertThat(errorOf(() -> service.chooseOutcome(ME, JOURNEY_ID, JourneyChoiceMode.BUS, NOW)))
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(rideRequestRepository);
    }
}
