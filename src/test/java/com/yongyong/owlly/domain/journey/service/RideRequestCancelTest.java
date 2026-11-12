package com.yongyong.owlly.domain.journey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.RideRequestResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 대기 신청 취소(API-21)의 규칙: 출발 15분 전까지만 취소할 수 있다. 저장소는 가짜로 둔다. */
class RideRequestCancelTest {

    private static final long ME = 1L;
    private static final long REQUEST_ID = 500L;
    private static final long JOURNEY_ID = 10L;
    private static final Instant DEPARTURE = Instant.parse("2026-11-05T14:30:00Z");   // 23:30 KST

    private final RideRequestRepository rideRequestRepository = mock(RideRequestRepository.class);
    private final JourneyRepository journeyRepository = mock(JourneyRepository.class);
    private final RideRequestService service = new RideRequestService(
            rideRequestRepository, journeyRepository, mock(PartyMemberRepository.class), mock(PlaceNameReader.class));

    private RideRequest request(RideRequestStatus status) {
        RideRequest request = RideRequest.builder().id(REQUEST_ID).journeyId(JOURNEY_ID)
                .pickupPointId(1L).districtId(1L).destinationDongId(1L)
                .departureAt(DEPARTURE).status(status).build();
        when(rideRequestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(journeyRepository.findById(JOURNEY_ID)).thenReturn(Optional.of(Journey.builder().id(JOURNEY_ID).userId(ME).build()));
        return request;
    }

    private ErrorCode errorOf(Instant now) {
        try {
            service.cancelMyRideRequest(ME, REQUEST_ID, now);
        } catch (OwllyException e) {
            return e.getErrorCode();
        }
        return null;
    }

    @Test
    void 출발_15분_전까지는_취소할_수_있다() {
        RideRequest waiting = request(RideRequestStatus.WAITING);

        RideRequestResponse response = service.cancelMyRideRequest(ME, REQUEST_ID, Instant.parse("2026-11-05T14:15:00Z"));

        assertThat(waiting.getStatus()).isEqualTo(RideRequestStatus.CANCELLED);
        assertThat(response.status()).isEqualTo(RideRequestStatus.CANCELLED);
    }

    @Test
    void 출발_15분_전이_지나면_409이고_상태는_그대로다() {
        RideRequest waiting = request(RideRequestStatus.WAITING);

        assertThat(errorOf(Instant.parse("2026-11-05T14:15:01Z"))).isEqualTo(ErrorCode.CANCELLATION_DEADLINE_PASSED);

        assertThat(waiting.getStatus()).isEqualTo(RideRequestStatus.WAITING);
    }

    @Test
    void 이미_취소했으면_마감이_지난_뒤에도_같은_응답() {
        request(RideRequestStatus.CANCELLED);

        RideRequestResponse response = service.cancelMyRideRequest(ME, REQUEST_ID, Instant.parse("2026-11-05T14:20:00Z"));

        assertThat(response.status()).isEqualTo(RideRequestStatus.CANCELLED);
    }

    @Test
    void 매칭된_신청은_마감_전에도_409_팟에서_나가야_한다_끝난_신청은_INVALID_STATE() {
        Instant early = Instant.parse("2026-11-05T13:00:00Z");

        request(RideRequestStatus.MATCHED);
        assertThat(errorOf(early)).isEqualTo(ErrorCode.REQUEST_ALREADY_MATCHED);
        request(RideRequestStatus.FAILED);
        assertThat(errorOf(early)).isEqualTo(ErrorCode.INVALID_STATE);
        request(RideRequestStatus.EXPIRED);
        assertThat(errorOf(early)).isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void 남의_신청은_404() {
        request(RideRequestStatus.WAITING);
        when(journeyRepository.findById(JOURNEY_ID)).thenReturn(Optional.of(Journey.builder().id(JOURNEY_ID).userId(999L).build()));

        assertThat(errorOf(Instant.parse("2026-11-05T13:00:00Z"))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
