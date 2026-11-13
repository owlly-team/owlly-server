package com.yongyong.owlly.domain.transit.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;
import com.yongyong.owlly.domain.journey.dto.MatchingSlot;
import com.yongyong.owlly.domain.journey.service.MatchingSlotService;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.region.service.OutOfCityDetector;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.DayType;
import com.yongyong.owlly.domain.transit.client.GbisBusClient.GbisException;
import com.yongyong.owlly.domain.transit.dto.TransitLeg;
import com.yongyong.owlly.domain.transit.dto.TransitOption;
import com.yongyong.owlly.domain.transit.dto.TravelOptions;
import com.yongyong.owlly.domain.transit.dto.TravelOptionsRequest;
import com.yongyong.owlly.domain.transit.service.TransitPlanCatalog.TransitPlan;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.StudentProfile;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 막차·동승·혼자 택시 비교 (API-14).
 *
 * <p>상황: 학생이 앱을 열면 홈이 "지금 어떻게 갈까?"를 한눈에 보여 준다(화면정의서 S-01 홈·막차 전, 막차 경로, 혼자 택시, 홈·막차 후).
 * 막차(버스 시간표 기준), 혼자 택시(지금 출발), 동승(가장 가까운 시간대와 예상 분담액)을 한 번에 계산한다.
 * 외부 자료가 비면 그 값만 비우고 warnings 에 알린다. 하차 핀과 현재 위치는 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelOptionsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 정류장에 도착해 버스를 기다리는 여유. 화면정의서 홈·막차 전: 출발해야 할 시각 = 막차 출발 − 도보 시간 − 5분. */
    static final int LEAVE_BUFFER_MINUTES = 5;
    /** 한 팟의 최대 인원. */
    private static final int MAX_PARTY_SIZE = 4;
    /** 새벽 3시 전에 출발하는 막차는 다음 날 새벽으로 본다(운행일은 밤 기준). */
    private static final LocalTime SERVICE_DAY_CHANGES_AT = LocalTime.of(3, 0);

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PickupPointRepository pickupPointRepository;
    private final AdministrativeAreaRepository administrativeAreaRepository;
    private final OutOfCityDetector outOfCityDetector;
    private final DropoffLocationService dropoffLocations;
    private final TaxiFareService taxiFareService;
    private final MatchingSlotService matchingSlotService;
    private final TransitPlanCatalog transitPlanCatalog;
    private final TransitTimetableService timetableService;

    /**
     * 세 가지 귀가 방법을 비교한다.
     * - 학생이 아니면 403, 거점이 없거나 내 학교가 쓸 수 없으면 404, 하차 핀의 행정동을 못 찾으면 422, 카카오 판별이 안 되면 503.
     * - 막차: 안내할 구간이 아니거나 막차가 끊겼거나 시간표를 못 가져오면 available=false (오류가 아니다).
     * - 혼자 택시: 지금 출발 기준 요금·거리·시간. 소요 시간을 모르면 도착 시각만 비운다.
     * - 동승: 가장 가까운 시간대의 대기 인원이 있으면 "혼자 택시 요금 ÷ (대기 인원 + 나, 최대 4명)"으로 어림한 분담액.
     */
    public TravelOptions compare(Long userId, TravelOptionsRequest request, Instant now) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (user.getRole() != Role.STUDENT) {
            throw new OwllyException(ErrorCode.FORBIDDEN);
        }
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        PickupPoint pickupPoint = pickupPointRepository.findById(request.pickupPointId())
                .filter(p -> p.isUsableBy(user.getUniversityId()))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        AdministrativeArea dong = dropoffLocations.resolveDong(request.dropoff().latitude(), request.dropoff().longitude());
        AdministrativeArea district = administrativeAreaRepository.findById(dong.getParentId())
                .orElseThrow(() -> new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE));

        GeoPoint origin = new GeoPoint(pickupPoint.getLatitude().doubleValue(), pickupPoint.getLongitude().doubleValue());
        GeoPoint dropoff = new GeoPoint(request.dropoff().latitude(), request.dropoff().longitude());
        GeoPoint walkingStart = (request.currentLocation() == null)
                ? origin
                : new GeoPoint(request.currentLocation().latitude(), request.currentLocation().longitude());
        List<String> warnings = new ArrayList<>();

        TransitOption transit = transitOption(
                transitPlanCatalog.find(pickupPoint.getName(), district.getOfficialCode()),
                walkingStart, dropoff, request.departureAt(), now, warnings);

        boolean outOfCity = outOfCityDetector.isOutOfCity(pickupPoint.getAreaId(), List.of(district.getId()));
        RouteEstimate taxi = taxiFareService.estimateRoute(origin, List.of(dropoff), now, outOfCity);
        if (outOfCity) {
            warnings.add("시·군을 넘어가서 시계 외 할증을 반영했어요");
        }
        if (taxi.routingSource().startsWith("YONGIN")) {
            warnings.add("카카오 길찾기를 쓸 수 없어 요금표 공식으로 어림했어요");
        }
        Integer taxiMinutes = (taxi.durationSec() == null) ? null : (int) Math.ceil(taxi.durationSec() / 60.0);
        Instant taxiArrival = (taxi.durationSec() == null) ? null : now.plusSeconds(taxi.durationSec());
        if (taxi.durationSec() == null) {
            warnings.add("택시 소요 시간을 알 수 없어요");
        }

        Optional<MatchingSlot> nearestSlot = nearestOpenSlot(userId, pickupPoint.getId(), dong.getId(),
                profile.isSameGenderDefault(), now);
        Integer poolShare = nearestSlot
                .filter(slot -> slot.waitingCount() > 0)
                .map(slot -> roundUpTo100(taxi.totalFareWon() / (double) Math.min(slot.waitingCount() + 1, MAX_PARTY_SIZE)))
                .orElse(null);

        return new TravelOptions(
                transit,
                taxi.totalFareWon(),
                taxi.totalDistanceM(),
                taxiMinutes,
                taxiArrival,
                poolShare,
                nearestSlot.orElse(null),
                dong.getId(),
                dong.getName(),
                district.getId(),
                district.getName(),
                now,
                List.copyOf(warnings));
    }

    /** 지금 등록할 수 있는 가장 가까운 동승 시간대(오늘, 없으면 내일). */
    private Optional<MatchingSlot> nearestOpenSlot(Long userId, Long pickupPointId, Long dongId, boolean sameGenderOnly, Instant now) {
        LocalDate today = now.atZone(KST).toLocalDate();
        return Stream.of(today, today.plusDays(1))
                .flatMap(date -> matchingSlotService.getSlots(userId, pickupPointId, dongId, date, sameGenderOnly, now).stream())
                .filter(MatchingSlot::registrationAllowed)
                .findFirst();
    }

    /**
     * 막차로 가는 방법을 만든다.
     * 막차(버스가 종점이나 기점을 떠나는 시각)에 정류장까지 오는 시간을 더해 정류장 출발 시각을 구하고,
     * 걸어서 정류장에 가는 시간과 여유 5분을 빼 "이 시각까지 출발해야 해요"를 만든다.
     */
    private TransitOption transitOption(Optional<TransitPlan> planOrEmpty, GeoPoint walkingStart, GeoPoint dropoff,
                                        Instant departureAt, Instant now, List<String> warnings) {
        if (planOrEmpty.isEmpty()) {
            warnings.add("이 구간은 막차 안내를 아직 지원하지 않아요");
            return unavailable(now, "막차 안내를 지원하지 않는 구간");
        }
        TransitPlan plan = planOrEmpty.get();

        // 운행일: 새벽에 보는 막차는 전날 밤 운행에 속한다.
        LocalDate serviceDate = departureAt.atZone(KST).minusHours(3).toLocalDate();
        Optional<LocalTime> lastTime;
        try {
            lastTime = timetableService.lastDeparture(plan.routeId(), dayTypeOf(serviceDate), plan.fromTerminal());
        } catch (GbisException e) {
            warnings.add("버스 시간표를 불러오지 못해 막차를 안내할 수 없어요");
            return unavailable(now, "버스 시간표를 불러오지 못함");
        }
        if (lastTime.isEmpty()) {
            warnings.add("이 노선은 오늘 막차 정보가 없어요");
            return unavailable(now, "막차 정보 없음");
        }

        LocalDate lastDate = lastTime.get().isBefore(SERVICE_DAY_CHANGES_AT) ? serviceDate.plusDays(1) : serviceDate;
        Instant boardDeparture = ZonedDateTime.of(lastDate, lastTime.get(), KST).toInstant()
                .plus(Duration.ofMinutes(plan.minutesFromTerminalToBoard()));
        int walkToStopM = WalkingTime.distanceM(walkingStart, plan.boardStop());
        int walkToStopMin = WalkingTime.minutes(walkToStopM);
        if (now.isAfter(boardDeparture.minus(Duration.ofMinutes(walkToStopMin)))) {
            return unavailable(now, "막차가 이미 끊김");   // 막차가 끊겼으면 화면에서 막차 카드를 숨긴다
        }

        Instant lastLeaveAt = boardDeparture.minus(Duration.ofMinutes(walkToStopMin + LEAVE_BUFFER_MINUTES));
        Instant busArrival = boardDeparture.plus(Duration.ofMinutes(plan.rideMinutes()));
        int walkToHomeM = WalkingTime.distanceM(plan.alightStop(), dropoff);
        Instant arrivalAt = busArrival.plus(Duration.ofMinutes(WalkingTime.minutes(walkToHomeM)));

        List<TransitLeg> legs = List.of(
                new TransitLeg("WALK", null, "출발 위치", plan.boardStopName() + " 정류장", lastLeaveAt,
                        lastLeaveAt.plus(Duration.ofMinutes(walkToStopMin)), walkToStopM, null, false),
                new TransitLeg("BUS", plan.routeName(), plan.boardStopName() + " 정류장", plan.alightStopName(),
                        boardDeparture, busArrival, null, plan.stopCount(), true),
                new TransitLeg("WALK", null, plan.alightStopName(), "하차 지점", busArrival, arrivalAt, walkToHomeM, null, false));
        warnings.add("버스·도보 소요 시간은 추정이에요");
        return new TransitOption(true, lastLeaveAt, arrivalAt, plan.fareWon(),
                (int) Duration.between(boardDeparture, arrivalAt).toMinutes(), legs,
                "경기도 버스 시간표(GBIS) 기준 막차, 소요 시간은 추정", now);
    }

    private static TransitOption unavailable(Instant now, String sourceLabel) {
        return new TransitOption(false, null, null, null, null, List.of(), sourceLabel, now);
    }

    /** GBIS 는 평일·토·일·공휴일을 따로 준다. 공휴일 판정은 아직 없어서 평일·토·일만 가른다. */
    static DayType dayTypeOf(LocalDate serviceDate) {
        DayOfWeek day = serviceDate.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) {
            return DayType.SATURDAY;
        }
        return day == DayOfWeek.SUNDAY ? DayType.SUNDAY : DayType.WEEKDAY;
    }

    private static int roundUpTo100(double won) {
        return (int) Math.ceil(won / 100) * 100;
    }
}
