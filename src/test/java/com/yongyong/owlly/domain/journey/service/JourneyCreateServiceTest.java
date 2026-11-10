package com.yongyong.owlly.domain.journey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.JourneyCreate;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.dto.Point;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 귀가 생성(API-15)의 업무 규칙. 저장소와 외부 판별은 가짜로 둔다. */
class JourneyCreateServiceTest {

    private static final long ME = 1L;
    private static final long UNIVERSITY = 7L;
    private static final long PICKUP = 11L;
    private static final long DONG = 105L;
    private static final UUID KEY = UUID.fromString("0b18e222-668f-4b29-a013-6f1169e7a001");
    private static final Instant DEPART = Instant.parse("2026-11-05T14:30:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PickupPointRepository pickupPointRepository = mock(PickupPointRepository.class);
    private final JourneyRepository journeyRepository = mock(JourneyRepository.class);
    private final RideRequestRepository rideRequestRepository = mock(RideRequestRepository.class);
    private final DropoffLocationService dropoffLocations = mock(DropoffLocationService.class);
    private final JourneyService journeyService =
            new JourneyService(journeyRepository, rideRequestRepository, mock(PlaceNameReader.class));
    private final JourneyCreateService service = new JourneyCreateService(
            userRepository, pickupPointRepository, journeyRepository, rideRequestRepository, dropoffLocations, journeyService);

    private AdministrativeArea dong(long id) {
        return AdministrativeArea.builder().id(id).parentId(21L).level(AreaLevel.DONG).name("영통3동").officialCode("4111758500").build();
    }

    private JourneyCreate request(long pickupId, Instant departureAt) {
        return new JourneyCreate(KEY, pickupId, new Point(37.33, 127.27), departureAt);
    }

    private void pickup(Long universityId, boolean active) {
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(
                PickupPoint.builder().id(PICKUP).universityId(universityId).active(active).build()));
    }

    private void environment() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(User.builder().universityId(UNIVERSITY).build()));
        pickup(UNIVERSITY, true);
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble())).thenReturn(dong(DONG));
        when(journeyRepository.findByClientJourneyKey(KEY)).thenReturn(Optional.empty());
        when(rideRequestRepository.existsActiveByUserId(anyLong(), anyCollection())).thenReturn(false);
        when(journeyRepository.save(any(Journey.class))).thenAnswer(call -> call.getArgument(0));
    }

    private ErrorCode errorOf(Runnable call) {
        try {
            call.run();
        } catch (OwllyException e) {
            return e.getErrorCode();
        }
        return null;
    }

    @Test
    void 학교는_로그인한_학생의_학교로_적고_하차는_행정동만_저장한다() {
        environment();

        JourneyResponse response = service.create(ME, request(PICKUP, DEPART));

        assertThat(response.outcome()).isEqualTo(JourneyOutcome.UNRESOLVED);
        assertThat(response.universityId()).isEqualTo(UNIVERSITY);
        assertThat(response.pickupPointId()).isEqualTo(PICKUP);
        assertThat(response.destinationDongId()).isEqualTo(DONG);
        assertThat(response.intendedDepartureAt()).isEqualTo(DEPART);
        assertThat(response.activeRequestId()).isNull();
        verify(journeyRepository).lockUser(ME);
    }

    @Test
    void 공용_거점은_어느_학교_학생이든_쓸_수_있다() {
        environment();
        pickup(null, true);

        assertThat(service.create(ME, request(PICKUP, DEPART)).pickupPointId()).isEqualTo(PICKUP);
    }

    @Test
    void 없는_거점_비활성_거점_다른_학교_전용_거점은_똑같이_404() {
        environment();
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.empty());
        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        pickup(UNIVERSITY, false);
        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        pickup(UNIVERSITY + 1, true);
        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(journeyRepository, never()).save(any());
    }

    @Test
    void 하차_위치의_행정동을_못_찾으면_그_오류를_그대로_돌려준다() {
        environment();
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble()))
                .thenThrow(new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE));

        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.LOCATION_UNRESOLVABLE);
        verify(journeyRepository, never()).save(any());
    }

    @Test
    void 같은_키로_같은_요청을_다시_보내면_먼저_만든_귀가를_돌려주고_새로_만들지_않는다() {
        environment();
        Journey first = Journey.builder().id(50L).clientJourneyKey(KEY).userId(ME).universityId(UNIVERSITY)
                .pickupPointId(PICKUP).destinationDongId(DONG).intendedDepartureAt(DEPART)
                .outcome(JourneyOutcome.UNRESOLVED).build();
        when(journeyRepository.findByClientJourneyKey(KEY)).thenReturn(Optional.of(first));

        JourneyResponse response = service.create(ME, request(PICKUP, DEPART));

        assertThat(response.id()).isEqualTo(50L);
        verify(journeyRepository, never()).save(any());
    }

    @Test
    void 같은_키로_다른_요청이면_409() {
        environment();
        Journey first = Journey.builder().id(50L).clientJourneyKey(KEY).userId(ME).universityId(UNIVERSITY)
                .pickupPointId(PICKUP).destinationDongId(DONG).intendedDepartureAt(DEPART)
                .outcome(JourneyOutcome.UNRESOLVED).build();
        when(journeyRepository.findByClientJourneyKey(KEY)).thenReturn(Optional.of(first));

        // 시각이 다른 경우
        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART.plusSeconds(1800)))))
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        // 하차 동이 다른 경우
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble())).thenReturn(dong(DONG + 1));
        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        // 다른 학생이 만든 키인 경우
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble())).thenReturn(dong(DONG));
        Journey others = Journey.builder().id(51L).clientJourneyKey(KEY).userId(999L).universityId(UNIVERSITY)
                .pickupPointId(PICKUP).destinationDongId(DONG).intendedDepartureAt(DEPART)
                .outcome(JourneyOutcome.UNRESOLVED).build();
        when(journeyRepository.findByClientJourneyKey(KEY)).thenReturn(Optional.of(others));
        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void 다른_귀가에_진행_중인_동승이_있으면_409이고_만들지_않는다() {
        environment();
        when(rideRequestRepository.existsActiveByUserId(anyLong(), anyCollection())).thenReturn(true);

        assertThat(errorOf(() -> service.create(ME, request(PICKUP, DEPART)))).isEqualTo(ErrorCode.ACTIVE_JOURNEY_CONFLICT);
        verify(journeyRepository, never()).save(any());
    }

    @Test
    void 로그인한_학생이_없으면_404() {
        when(userRepository.findById(ME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ME, request(PICKUP, DEPART)))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
