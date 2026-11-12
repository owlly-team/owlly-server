package com.yongyong.owlly.domain.journey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.MatchingSlot;
import com.yongyong.owlly.domain.journey.dto.WaitingRequestRow;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository.BlockedPair;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.user.entity.Gender;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.StudentProfile;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 출발 시간대별 대기 인원(API-18)의 규칙: 같은 팟에 들어갈 수 있는 신청만 센다. */
class MatchingSlotServiceTest {

    private static final long ME = 1L;
    private static final long UNIVERSITY = 7L;
    private static final long PICKUP = 11L;
    private static final long DONG = 105L;
    private static final long DISTRICT = 21L;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);
    private static final Instant SLOT_2300 = kst("2026-11-05T23:00:00");
    private static final Instant SLOT_2330 = kst("2026-11-05T23:30:00");
    private static final Instant NOW = kst("2026-11-05T22:32:00");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
    private final PickupPointRepository pickupPointRepository = mock(PickupPointRepository.class);
    private final AdministrativeAreaRepository areaRepository = mock(AdministrativeAreaRepository.class);
    private final RideRequestRepository rideRequestRepository = mock(RideRequestRepository.class);
    private final RidePartyRepository ridePartyRepository = mock(RidePartyRepository.class);
    private final MatchingSlotService service = new MatchingSlotService(userRepository, profileRepository,
            pickupPointRepository, areaRepository, rideRequestRepository, ridePartyRepository,
            new DepartureSlotPolicy("22:00", "02:00"));

    private static Instant kst(String dateTime) {
        return ZonedDateTime.parse(dateTime + "+09:00[Asia/Seoul]").toInstant();
    }

    private void environment(Gender myGender) {
        when(userRepository.findById(ME)).thenReturn(Optional.of(
                User.builder().id(ME).universityId(UNIVERSITY).role(Role.STUDENT).build()));
        when(profileRepository.findById(ME)).thenReturn(Optional.of(StudentProfile.builder().userId(ME).gender(myGender).build()));
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(
                PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY).active(true).build()));
        when(areaRepository.findById(DONG)).thenReturn(Optional.of(
                AdministrativeArea.builder().id(DONG).parentId(DISTRICT).level(AreaLevel.DONG).build()));
        when(rideRequestRepository.findWaitingForSlots(eq(UNIVERSITY), eq(PICKUP), eq(DISTRICT), anyCollection()))
                .thenReturn(List.of());
        when(ridePartyRepository.findBlockedPairs(anyCollection())).thenReturn(List.of());
    }

    private void waiting(WaitingRequestRow... rows) {
        when(rideRequestRepository.findWaitingForSlots(eq(UNIVERSITY), eq(PICKUP), eq(DISTRICT), anyCollection()))
                .thenReturn(List.of(rows));
    }

    private static WaitingRequestRow row(Instant slot, long userId, Gender gender, boolean sameGenderOnly) {
        return new WaitingRequestRow(slot, userId, gender, sameGenderOnly);
    }

    private int countAt(List<MatchingSlot> slots, Instant slot) {
        return slots.stream().filter(s -> s.departureAt().equals(slot)).findFirst().orElseThrow().waitingCount();
    }

    private static BlockedPair blocked(long low, long high) {
        BlockedPair pair = mock(BlockedPair.class);
        when(pair.getLowUserId()).thenReturn(low);
        when(pair.getHighUserId()).thenReturn(high);
        return pair;
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
    void 그날의_시간대마다_대기_인원을_센다_내_신청은_뺀다() {
        environment(Gender.FEMALE);
        waiting(row(SLOT_2300, 2L, Gender.FEMALE, false), row(SLOT_2330, 3L, Gender.FEMALE, false),
                row(SLOT_2330, 4L, Gender.FEMALE, false), row(SLOT_2330, ME, Gender.FEMALE, false));

        List<MatchingSlot> slots = service.getSlots(ME, PICKUP, DONG, DATE, false, NOW);

        assertThat(slots).hasSize(9);
        assertThat(countAt(slots, SLOT_2300)).isEqualTo(1);
        assertThat(countAt(slots, SLOT_2330)).isEqualTo(2);
        assertThat(countAt(slots, kst("2026-11-05T22:00:00"))).isZero();
    }

    @Test
    void 성별_조건이_맞지_않으면_세지_않는다_한쪽이라도_동성만_원하면_성별이_같아야_한다() {
        environment(Gender.FEMALE);
        waiting(row(SLOT_2300, 2L, Gender.MALE, false),     // 남성, 동성 조건 없음
                row(SLOT_2300, 3L, Gender.FEMALE, true),    // 여성, 동성만
                row(SLOT_2300, 4L, Gender.MALE, true));     // 남성, 동성만 → 나(여성)와 안 맞음

        // 내가 동성 조건을 끄면: 남성(조건 없음)과 여성(동성만)이 맞는다. 남성(동성만)은 안 맞는다.
        assertThat(countAt(service.getSlots(ME, PICKUP, DONG, DATE, false, NOW), SLOT_2300)).isEqualTo(2);
        // 내가 동성 조건을 켜면: 여성만 맞는다.
        assertThat(countAt(service.getSlots(ME, PICKUP, DONG, DATE, true, NOW), SLOT_2300)).isEqualTo(1);
    }

    @Test
    void 나와_서로_차단한_학생은_세지_않는다() {
        environment(Gender.FEMALE);
        waiting(row(SLOT_2300, 2L, Gender.FEMALE, false), row(SLOT_2300, 3L, Gender.FEMALE, false));
        BlockedPair pair = blocked(1L, 2L);
        when(ridePartyRepository.findBlockedPairs(anyCollection())).thenReturn(List.of(pair));

        assertThat(countAt(service.getSlots(ME, PICKUP, DONG, DATE, false, NOW), SLOT_2300)).isEqualTo(1);
    }

    @Test
    void 출발_15분_전이_지난_시간대는_목록에_있고_등록만_못_한다() {
        environment(Gender.FEMALE);

        List<MatchingSlot> slots = service.getSlots(ME, PICKUP, DONG, DATE, false, NOW);

        MatchingSlot closed = slots.stream().filter(s -> s.departureAt().equals(kst("2026-11-05T22:30:00"))).findFirst().orElseThrow();
        assertThat(closed.registrationAllowed()).isFalse();
        assertThat(closed.reason()).isEqualTo("REGISTRATION_CLOSED");
        MatchingSlot open = slots.stream().filter(s -> s.departureAt().equals(SLOT_2300)).findFirst().orElseThrow();
        assertThat(open.registrationAllowed()).isTrue();
        assertThat(open.reason()).isNull();
    }

    @Test
    void 학생이_아니면_403_거점이나_도착_동이_이상하면_404() {
        environment(Gender.FEMALE);
        when(userRepository.findById(ME)).thenReturn(Optional.of(
                User.builder().id(ME).universityId(UNIVERSITY).role(Role.STAFF).build()));
        assertThat(errorOf(() -> service.getSlots(ME, PICKUP, DONG, DATE, false, NOW))).isEqualTo(ErrorCode.FORBIDDEN);

        environment(Gender.FEMALE);
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(
                PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY + 1).active(true).build()));
        assertThat(errorOf(() -> service.getSlots(ME, PICKUP, DONG, DATE, false, NOW))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        environment(Gender.FEMALE);
        when(areaRepository.findById(DONG)).thenReturn(Optional.of(
                AdministrativeArea.builder().id(DONG).parentId(DISTRICT).level(AreaLevel.DISTRICT).build()));
        assertThat(errorOf(() -> service.getSlots(ME, PICKUP, DONG, DATE, false, NOW))).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
