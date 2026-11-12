package com.yongyong.owlly.domain.transit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;
import com.yongyong.owlly.domain.journey.dto.MatchingSlot;
import com.yongyong.owlly.domain.journey.dto.Point;
import com.yongyong.owlly.domain.journey.service.MatchingSlotService;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.region.service.OutOfCityDetector;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayType;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.GbisException;
import com.yongyong.owlly.domain.transit.dto.TransitOption;
import com.yongyong.owlly.domain.transit.dto.TravelOptions;
import com.yongyong.owlly.domain.transit.dto.TravelOptionsRequest;
import com.yongyong.owlly.domain.user.entity.Gender;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.StudentProfile;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 막차·동승·혼자 택시 비교(API-14)의 규칙: 시연 구간 막차 계산, 끊김·자료 부족 처리, 동승 분담 추정. */
class TravelOptionsServiceTest {

    private static final long ME = 1L;
    private static final long UNIVERSITY = 7L;
    private static final long PICKUP = 11L;
    private static final long PICKUP_CITY = 30L;   // 거점이 있는 시(용인시). 하차 지점의 시(수원시, id 20)와 다르다
    private static final Instant DEPARTURE = kst("2026-11-05T23:30:00");   // 목요일
    private static final Instant NOW = kst("2026-11-05T22:31:00");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
    private final PickupPointRepository pickupPointRepository = mock(PickupPointRepository.class);
    private final AdministrativeAreaRepository areaRepository = mock(AdministrativeAreaRepository.class);
    private final DropoffLocationService dropoffLocations = mock(DropoffLocationService.class);
    private final TaxiFareService taxiFareService = mock(TaxiFareService.class);
    private final MatchingSlotService matchingSlotService = mock(MatchingSlotService.class);
    private final TransitTimetableService timetable = mock(TransitTimetableService.class);
    private final TravelOptionsService service = new TravelOptionsService(userRepository, profileRepository,
            pickupPointRepository, areaRepository, new OutOfCityDetector(areaRepository), dropoffLocations, taxiFareService, matchingSlotService,
            new TransitPlanCatalog(PICKUP), timetable);

    private static Instant kst(String dateTime) {
        return ZonedDateTime.parse(dateTime + "+09:00[Asia/Seoul]").toInstant();
    }

    private void environment(String districtCode) {
        when(userRepository.findById(ME)).thenReturn(Optional.of(User.builder().id(ME).universityId(UNIVERSITY).role(Role.STUDENT).build()));
        when(profileRepository.findById(ME)).thenReturn(Optional.of(
                StudentProfile.builder().userId(ME).gender(Gender.FEMALE).sameGenderDefault(true).build()));
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY).active(true).areaId(PICKUP_CITY)
                .latitude(new BigDecimal("37.3219")).longitude(new BigDecimal("127.1265")).build()));
        when(dropoffLocations.resolveDong(anyDouble(), anyDouble())).thenReturn(
                AdministrativeArea.builder().id(105L).parentId(21L).level(AreaLevel.DONG).name("영통3동").officialCode("4111758500").build());
        when(areaRepository.findById(21L)).thenReturn(Optional.of(
                AdministrativeArea.builder().id(21L).parentId(20L).level(AreaLevel.DISTRICT).name("영통구").officialCode(districtCode).build()));
        when(areaRepository.findById(20L)).thenReturn(Optional.of(
                AdministrativeArea.builder().id(20L).level(AreaLevel.CITY).name("수원시").officialCode("4111000000").build()));
        when(areaRepository.findById(PICKUP_CITY)).thenReturn(Optional.of(
                AdministrativeArea.builder().id(PICKUP_CITY).level(AreaLevel.CITY).name("용인시").officialCode("4146000000").build()));
        when(taxiFareService.estimateRoute(any(), anyList(), any(), anyBoolean())).thenReturn(
                new RouteEstimate(List.of(21_000), 21_000, 27_400, "KAKAO_DIRECTIONS_V1", 2_040));
        when(matchingSlotService.getSlots(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(timetable.lastDeparture(anyLong(), any(DayType.class), anyBoolean())).thenReturn(Optional.of(LocalTime.of(23, 0)));
    }

    private static double anyDouble() {
        return org.mockito.ArgumentMatchers.anyDouble();
    }

    private TravelOptionsRequest request(Point currentLocation) {
        return new TravelOptionsRequest(PICKUP, new Point(37.2519, 127.0729), DEPARTURE, currentLocation);
    }

    private TravelOptions compare(Instant now) {
        return service.compare(ME, request(null), now);
    }

    @Test
    void 시연_구간은_막차_출발에서_도보와_여유를_빼서_출발해야_할_시각을_계산한다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);

        TransitOption transit = compare(NOW).transit();

        assertThat(transit.available()).isTrue();
        assertThat(transit.legs()).hasSize(3);
        assertThat(transit.legs().get(1).mode()).isEqualTo("BUS");
        assertThat(transit.legs().get(1).routeName()).isEqualTo("720-3");
        assertThat(transit.legs().get(1).departureAt()).as("종점 23:00 출발 + 정류장까지 5분").isEqualTo(kst("2026-11-05T23:05:00"));
        assertThat(transit.legs().get(1).arrivalAt()).as("버스 40분").isEqualTo(kst("2026-11-05T23:45:00"));
        assertThat(transit.legs().get(1).stopCount()).isEqualTo(41);
        assertThat(transit.legs().get(1).lastBus()).isTrue();
        // 출발해야 할 시각 = 정류장 출발 − 도보 − 5분, 첫 도보 구간은 그 시각에 시작해 도보 시간 뒤 끝난다
        int walkMin = (int) java.time.Duration.between(transit.legs().get(0).departureAt(), transit.legs().get(0).arrivalAt()).toMinutes();
        assertThat(transit.lastLeaveAt()).isEqualTo(kst("2026-11-05T23:05:00").minusSeconds((walkMin + 5) * 60L));
        assertThat(transit.estimatedCostWon()).isEqualTo(1_550);
        assertThat(transit.arrivalAt()).isEqualTo(transit.legs().get(2).arrivalAt());
        assertThat(transit.totalDurationMin()).isEqualTo(
                (int) java.time.Duration.between(transit.legs().get(1).departureAt(), transit.arrivalAt()).toMinutes());
    }

    @Test
    void 막차가_이미_끊겼으면_안내하지_않는다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);

        assertThat(compare(kst("2026-11-05T23:30:00")).transit().available()).isFalse();
        assertThat(compare(kst("2026-11-05T23:30:00")).transit().legs()).isEmpty();
    }

    @Test
    void 안내하지_않는_구간이면_막차만_비우고_경고하고_택시는_그대로() {
        environment("1168000000");

        TravelOptions result = compare(NOW);

        assertThat(result.transit().available()).isFalse();
        assertThat(result.warnings()).contains("이 구간은 막차 안내를 아직 지원하지 않아요");
        assertThat(result.soloTaxiEstimatedWon()).isEqualTo(27_400);
    }

    @Test
    void GBIS를_못_쓰면_막차만_비우고_나머지는_정상_응답() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(timetable.lastDeparture(anyLong(), any(DayType.class), anyBoolean())).thenThrow(new GbisException("장애"));

        TravelOptions result = compare(NOW);

        assertThat(result.transit().available()).isFalse();
        assertThat(result.warnings()).contains("버스 시간표를 불러오지 못해 막차를 안내할 수 없어요");
        assertThat(result.soloTaxiEstimatedWon()).isEqualTo(27_400);
    }

    @Test
    void 혼자_택시는_지금_출발_기준_요금_거리_시간_도착이고_동_구_이름이_붙는다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);

        TravelOptions result = compare(NOW);

        assertThat(result.soloTaxiEstimatedWon()).isEqualTo(27_400);
        assertThat(result.soloTaxiDistanceM()).isEqualTo(21_000);
        assertThat(result.soloTaxiDurationMin()).isEqualTo(34);
        assertThat(result.estimatedArrivalAt()).isEqualTo(NOW.plusSeconds(2_040));
        assertThat(result.destinationDongId()).isEqualTo(105L);
        assertThat(result.destinationDongName()).isEqualTo("영통3동");
        assertThat(result.districtId()).isEqualTo(21L);
        assertThat(result.districtName()).isEqualTo("영통구");
        assertThat(result.asOf()).isEqualTo(NOW);
    }

    @Test
    void 택시_소요_시간을_모르면_도착_시각만_비우고_경고() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(taxiFareService.estimateRoute(any(), anyList(), any(), anyBoolean())).thenReturn(
                new RouteEstimate(List.of(21_000), 21_000, 27_400, "YONGIN_FARE_TABLE_2023+STRAIGHT_X1.3"));

        TravelOptions result = compare(NOW);

        assertThat(result.estimatedArrivalAt()).isNull();
        assertThat(result.soloTaxiDurationMin()).isNull();
        assertThat(result.warnings()).contains("택시 소요 시간을 알 수 없어요", "카카오 길찾기를 쓸 수 없어 요금표 공식으로 어림했어요");
    }

    @Test
    void 동승_분담은_가장_가까운_등록_가능_시간대의_대기_인원과_나를_더한_수로_요금을_나눈다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(matchingSlotService.getSlots(eq(ME), eq(PICKUP), eq(105L), any(), eq(true), any())).thenReturn(List.of(
                new MatchingSlot(kst("2026-11-05T22:30:00"), 5, false, "REGISTRATION_CLOSED"),
                new MatchingSlot(kst("2026-11-05T23:00:00"), 2, true, null),
                new MatchingSlot(kst("2026-11-05T23:30:00"), 1, true, null)));

        TravelOptions result = compare(NOW);

        assertThat(result.nearestPoolSlot().departureAt()).isEqualTo(kst("2026-11-05T23:00:00"));
        assertThat(result.poolEstimatedShareWon()).as("27,400 ÷ (2 + 나) 를 100원 단위로 올림").isEqualTo(9_200);
    }

    @Test
    void 대기_인원이_많아도_4명으로_나누고_0명이면_분담액을_비운다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(matchingSlotService.getSlots(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), any())).thenReturn(List.of(
                new MatchingSlot(kst("2026-11-05T23:00:00"), 9, true, null)));
        assertThat(compare(NOW).poolEstimatedShareWon()).isEqualTo(6_900);

        when(matchingSlotService.getSlots(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), any())).thenReturn(List.of(
                new MatchingSlot(kst("2026-11-05T23:00:00"), 0, true, null)));
        assertThat(compare(NOW).poolEstimatedShareWon()).isNull();

        when(matchingSlotService.getSlots(anyLong(), anyLong(), anyLong(), any(), anyBoolean(), any())).thenReturn(List.of());
        assertThat(compare(NOW).nearestPoolSlot()).isNull();
    }

    @Test
    void 학생이_아니면_403_쓸_수_없는_거점은_404() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(userRepository.findById(ME)).thenReturn(Optional.of(User.builder().id(ME).universityId(UNIVERSITY).role(Role.STAFF).build()));
        assertThat(errorOf()).isEqualTo(ErrorCode.FORBIDDEN);

        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY + 1).active(true)
                .latitude(new BigDecimal("37.3")).longitude(new BigDecimal("127.1")).build()));
        assertThat(errorOf()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private ErrorCode errorOf() {
        try {
            compare(NOW);
        } catch (OwllyException e) {
            return e.getErrorCode();
        }
        return null;
    }

    @Test
    void 요일_구분은_평일_토_일() {
        assertThat(TravelOptionsService.dayTypeOf(java.time.LocalDate.of(2026, 11, 5))).isEqualTo(DayType.WEEKDAY);
        assertThat(TravelOptionsService.dayTypeOf(java.time.LocalDate.of(2026, 11, 7))).isEqualTo(DayType.SATURDAY);
        assertThat(TravelOptionsService.dayTypeOf(java.time.LocalDate.of(2026, 11, 8))).isEqualTo(DayType.SUNDAY);
    }

    @Test
    void 거점과_하차_지점의_시가_다르면_시계_외_할증을_적용하고_같으면_적용하지_않는다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);

        TravelOptions crossing = compare(NOW);

        org.mockito.Mockito.verify(taxiFareService).estimateRoute(any(), anyList(), any(), eq(true));
        assertThat(crossing.warnings()).contains("시·군을 넘어가서 시계 외 할증을 반영했어요");

        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(pickupPointRepository.findById(PICKUP)).thenReturn(Optional.of(PickupPoint.builder().id(PICKUP).universityId(UNIVERSITY).active(true)
                .areaId(20L).latitude(new BigDecimal("37.3219")).longitude(new BigDecimal("127.1265")).build()));
        org.mockito.Mockito.clearInvocations(taxiFareService);

        TravelOptions sameCity = compare(NOW);

        org.mockito.Mockito.verify(taxiFareService).estimateRoute(any(), anyList(), any(), eq(false));
        assertThat(sameCity.warnings()).doesNotContain("시·군을 넘어가서 시계 외 할증을 반영했어요");
    }

    @Test
    void 거점의_행정구역을_알_수_없으면_시계_외_할증을_붙이지_않는다() {
        environment(TransitPlanCatalog.YEONGTONG_DISTRICT_CODE);
        when(areaRepository.findById(PICKUP_CITY)).thenReturn(Optional.empty());

        compare(NOW);

        org.mockito.Mockito.verify(taxiFareService).estimateRoute(any(), anyList(), any(), eq(false));
    }
}
