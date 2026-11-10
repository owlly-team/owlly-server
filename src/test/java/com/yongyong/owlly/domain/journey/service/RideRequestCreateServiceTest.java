package com.yongyong.owlly.domain.journey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.Point;
import com.yongyong.owlly.domain.journey.dto.RideRequestCreate;
import com.yongyong.owlly.domain.journey.dto.RideRequestResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import com.yongyong.owlly.domain.user.entity.Gender;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.StudentProfile;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 동승 신청(API-19)의 업무 규칙. 저장소와 외부 판별은 가짜로 둔다. */
class RideRequestCreateServiceTest {

    private static final long ME = 1L;
    private static final long UNIVERSITY = 7L;
    private static final long JOURNEY_ID = 10L;
    private static final long PICKUP = 11L;
    private static final long DONG = 105L;
    private static final long DISTRICT = 21L;
    private static final UUID KEY = UUID.fromString("0b18e222-668f-4b29-a013-6f1169e7a002");
    private static final Instant DEPART = kst("2026-11-05T23:30:00");
    private static final Instant NOW = kst("2026-11-05T22:32:00");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
    private final PickupPointRepository pickupPointRepository = mock(PickupPointRepository.class);
    private final JourneyRepository journeyRepository = mock(JourneyRepository.class);
    private final RideRequestRepository rideRequestRepository = mock(RideRequestRepository.class);
    private final DropoffLocationService dropoffLocations = mock(DropoffLocationService.class);
    private final TextCryptor cryptor = mock(TextCryptor.class);
    private final RideRequestService rideRequestService = new RideRequestService(
            rideRequestRepository, journeyRepository, mock(PartyMemberRepository.class), mock(PlaceNameReader.class));
    private final RideRequestCreateService service = new RideRequestCreateService(userRepository, profileRepository,
            pickupPointRepository, journeyRepository, rideRequestRepository, dropoffLocations, cryptor,
            new DepartureSlotPolicy("22:00", "02:00"), rideRequestService);

    private static Instant kst(String dateTime) {
        return ZonedDateTime.parse(dateTime + "+09:00[Asia/Seoul]").toInstant();
    }

    private Journey journey(JourneyOutcome outcome) {
        Journey journey = Journey.builder().id(JOURNEY_ID).userId(ME).universityId(UNIVERSITY)
                .pickupPointId(1L).destinationDongId(1L).intendedDepartureAt(kst("2026-11-05T23:00:00"))
                .outcome(outcome).build();
        when(journeyRepository.findByIdForUpdate(JOURNEY_ID)).thenReturn(Optional.of(journey));
        return journey;
    }

    private void environment() {
        when(userRepository.findById(ME)).thenReturn(Optional.of(
                User.builder().id(ME).universityId(UNIVERSITY).role(Role.STUDENT).build()));
        when(profileRepository.findById(ME)).thenReturn(Optional.of(StudentProfile.builder().userId(ME).gender(Gender.FEMALE).build()));
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(
                PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY).active(true).build()));
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble())).thenReturn(
                AdministrativeArea.builder().id(DONG).parentId(DISTRICT).level(AreaLevel.DONG).build());
        when(cryptor.encrypt(anyString())).thenAnswer(call -> "enc:" + call.getArgument(0));
        when(rideRequestRepository.findByClientRequestKey(KEY)).thenReturn(Optional.empty());
        when(rideRequestRepository.existsActiveByUserId(anyLong(), anyCollection())).thenReturn(false);
        when(rideRequestRepository.save(any(RideRequest.class))).thenAnswer(call -> call.getArgument(0));
    }

    private RideRequestCreate request(Instant departureAt) {
        return new RideRequestCreate(KEY, PICKUP, new Point(37.33, 127.27), departureAt, true);
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
    void 신청은_대기로_시작하고_권역_성별_결과_안내_시각을_적고_좌표는_암호화한다() {
        environment();
        Journey journey = journey(JourneyOutcome.UNRESOLVED);

        RideRequestResponse response = service.create(ME, JOURNEY_ID, request(DEPART), NOW);

        ArgumentCaptor<RideRequest> saved = ArgumentCaptor.forClass(RideRequest.class);
        verify(rideRequestRepository).save(saved.capture());
        RideRequest created = saved.getValue();
        assertThat(created.getStatus()).isEqualTo(RideRequestStatus.WAITING);
        assertThat(created.getUniversityId()).isEqualTo(UNIVERSITY);
        assertThat(created.getPickupPointId()).isEqualTo(PICKUP);
        assertThat(created.getDestinationDongId()).isEqualTo(DONG);
        assertThat(created.getDistrictId()).isEqualTo(DISTRICT);
        assertThat(created.getGenderSnapshot()).isEqualTo(Gender.FEMALE);
        assertThat(created.isSameGenderOnly()).isTrue();
        assertThat(created.getDepartureAt()).isEqualTo(DEPART);
        assertThat(created.getResultDueAt()).isEqualTo(kst("2026-11-05T23:10:00"));
        assertThat(created.getDropoffLatEncrypted()).isEqualTo("enc:37.33");
        assertThat(created.getDropoffLngEncrypted()).isEqualTo("enc:127.27");
        assertThat(response.status()).isEqualTo(RideRequestStatus.WAITING);
        assertThat(response.partyId()).isNull();
        // 귀가의 거점·동·예정 시각이 이번 신청의 값으로 바뀐다.
        assertThat(journey.getPickupPointId()).isEqualTo(PICKUP);
        assertThat(journey.getDestinationDongId()).isEqualTo(DONG);
        assertThat(journey.getIntendedDepartureAt()).isEqualTo(DEPART);
    }

    @Test
    void 서비스_시간이_아니거나_30분_단위가_아니면_422() {
        environment();
        journey(JourneyOutcome.UNRESOLVED);

        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(kst("2026-11-05T21:30:00")), NOW)))
                .isEqualTo(ErrorCode.INVALID_DEPARTURE_SLOT);
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(kst("2026-11-06T02:30:00")), NOW)))
                .isEqualTo(ErrorCode.INVALID_DEPARTURE_SLOT);
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(kst("2026-11-05T23:10:00")), NOW)))
                .isEqualTo(ErrorCode.INVALID_DEPARTURE_SLOT);
        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    void 출발_15분_전이_지난_시간대는_422() {
        environment();
        journey(JourneyOutcome.UNRESOLVED);

        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), kst("2026-11-05T23:16:00"))))
                .isEqualTo(ErrorCode.INVALID_DEPARTURE_SLOT);
        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    void 귀가_결과가_이미_정해졌으면_409() {
        environment();
        journey(JourneyOutcome.BUS_SELECTED);

        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW)))
                .isEqualTo(ErrorCode.JOURNEY_ALREADY_RESOLVED);
        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    void 진행_중인_동승이_이미_있으면_409() {
        environment();
        journey(JourneyOutcome.UNRESOLVED);
        when(rideRequestRepository.existsActiveByUserId(anyLong(), anyCollection())).thenReturn(true);

        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW)))
                .isEqualTo(ErrorCode.ACTIVE_REQUEST_EXISTS);
        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    void 같은_키로_같은_내용을_다시_보내면_먼저_만든_신청을_돌려주고_다시_만들지_않는다() {
        environment();
        journey(JourneyOutcome.UNRESOLVED);
        RideRequest first = RideRequest.builder().id(500L).clientRequestKey(KEY).journeyId(JOURNEY_ID)
                .universityId(UNIVERSITY).pickupPointId(PICKUP).districtId(DISTRICT).destinationDongId(DONG)
                .departureAt(DEPART).sameGenderOnly(true).genderSnapshot(Gender.FEMALE)
                .status(RideRequestStatus.WAITING).build();
        when(rideRequestRepository.findByClientRequestKey(KEY)).thenReturn(Optional.of(first));
        when(rideRequestRepository.findById(500L)).thenReturn(Optional.of(first));
        when(journeyRepository.findById(JOURNEY_ID)).thenReturn(Optional.of(
                Journey.builder().id(JOURNEY_ID).userId(ME).build()));

        RideRequestResponse response = service.create(ME, JOURNEY_ID, request(DEPART), NOW);

        assertThat(response.id()).isEqualTo(500L);
        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    void 같은_키로_다른_내용이면_409() {
        environment();
        journey(JourneyOutcome.UNRESOLVED);
        RideRequest first = RideRequest.builder().id(500L).clientRequestKey(KEY).journeyId(JOURNEY_ID)
                .universityId(UNIVERSITY).pickupPointId(PICKUP).districtId(DISTRICT).destinationDongId(DONG)
                .departureAt(DEPART).sameGenderOnly(false).genderSnapshot(Gender.FEMALE)
                .status(RideRequestStatus.WAITING).build();
        when(rideRequestRepository.findByClientRequestKey(KEY)).thenReturn(Optional.of(first));

        // 동성 조건이 다름
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW)))
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        // 시각이 다름
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(kst("2026-11-06T00:00:00")), NOW)))
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void 남의_귀가_없는_귀가_쓸_수_없는_거점은_404_학생이_아니면_403() {
        environment();
        when(journeyRepository.findByIdForUpdate(JOURNEY_ID)).thenReturn(Optional.of(
                Journey.builder().id(JOURNEY_ID).userId(999L).universityId(UNIVERSITY).outcome(JourneyOutcome.UNRESOLVED).build()));
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        when(journeyRepository.findByIdForUpdate(JOURNEY_ID)).thenReturn(Optional.empty());
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        journey(JourneyOutcome.UNRESOLVED);
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(
                PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY + 1).active(true).build()));
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        when(userRepository.findById(ME)).thenReturn(Optional.of(
                User.builder().id(ME).universityId(UNIVERSITY).role(Role.STAFF).build()));
        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW))).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 하차_위치를_판별하지_못하면_그_오류를_그대로_돌려준다() {
        environment();
        journey(JourneyOutcome.UNRESOLVED);
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble()))
                .thenThrow(new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE));

        assertThat(errorOf(() -> service.create(ME, JOURNEY_ID, request(DEPART), NOW))).isEqualTo(ErrorCode.LOCATION_UNRESOLVABLE);
        verify(rideRequestRepository, never()).save(any());
    }
}
