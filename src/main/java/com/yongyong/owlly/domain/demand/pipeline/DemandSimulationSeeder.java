package com.yongyong.owlly.domain.demand.pipeline;

import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.Gender;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 데모용 수요 원천 데이터 시더(공공데이터 미확보 상황의 시연용).
 *
 * <p>단국대 심야 귀가 세그먼트(정문→처인구 등)를 "금요일 23:30" 슬롯에 생성하고 {@link DemandRunGenerator} 로
 * 산출까지 돌려, 수요 지도·노선 후보·리포트가 실데이터처럼 뜨게 한다. 실제 서비스/공공데이터가 들어오면 불필요.
 *
 * <p>officialCode "DEMO-YONGIN-CITY" 존재 여부로 멱등 처리한다(이미 심었으면 다시 심지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandSimulationSeeder {

    private static final String DEMO_CITY_CODE = "DEMO-YONGIN-CITY";

    private final UniversityRepository universities;
    private final AdministrativeAreaRepository areas;
    private final PickupPointRepository pickups;
    private final JourneyRepository journeys;
    private final RideRequestRepository rideRequests;
    private final DemandRunGenerator generator;

    /** 한 노선 세그먼트의 데모 볼륨(관측 귀가 수·동승 신청 수·매칭 실패 수). 추정 주간수요 ≈ journeys × 3. */
    private record Segment(String pickupName, String dongCode, String dongName,
                           BigDecimal lat, BigDecimal lng, int journeys, int requests, int failed) {}

    /** 이미 심었으면 그 city 를, 아니면 전부 생성하고 산출까지 돌린 뒤의 안내. */
    public Optional<DemandRun> seedAndGenerateOnce() {
        if (areas.findByOfficialCode(DEMO_CITY_CODE).isPresent()) {
            log.info("데모 수요 데이터가 이미 있어 시드를 건너뜁니다(officialCode={}).", DEMO_CITY_CODE);
            return Optional.empty();
        }
        Long university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("단국대학교"))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("단국대학교 가 universities 에 없습니다.")).getId();

        AdministrativeArea city = area(DEMO_CITY_CODE, "용인시(데모)", AreaLevel.CITY, null, null, null);
        // 노선 후보 4개(Figma) + 표본 미달 1개. 추정 주간수요 ≈ journeys×3 → 141/105/87/78.
        List<Segment> segments = List.of(
                new Segment("단국대 정문", "DEMO-CHEOIN", "처인구", bd("37.2345"), bd("127.2010"), 47, 47, 17),
                new Segment("죽전역", "DEMO-CHEOIN", "처인구", bd("37.2345"), bd("127.2010"), 35, 35, 8),
                new Segment("단국대 정문", "DEMO-YEONGTONG", "영통구", bd("37.2590"), bd("127.0460"), 29, 29, 6),
                new Segment("강남역", "DEMO-GIHEUNG", "기흥구", bd("37.2800"), bd("127.1150"), 26, 26, 5),
                new Segment("단국대 정문", "DEMO-SUJI", "수지구", bd("37.3220"), bd("127.0970"), 3, 3, 1));

        LocalDate weekEnd = LocalDate.now(DemandAggregator.SERVICE_ZONE).minusDays(1);
        LocalDate weekStart = weekEnd.minusDays(6);
        Instant fridayNight = fridayNightWithin(weekStart, weekEnd);

        for (Segment s : segments) {
            PickupPoint pickup = findOrCreatePickup(university, city.getId(), s.pickupName());
            AdministrativeArea dong = area(s.dongCode(), s.dongName(), AreaLevel.DONG, city.getId(), s.lat(), s.lng());
            seedSegment(university, city.getId(), pickup.getId(), dong.getId(), s, fridayNight);
        }

        DemandRun run = generator.generate(weekStart, weekEnd);
        log.info("데모 수요 산출 완료 runId={} 기간={}~{} (금요일밤 {})", run.getId(), weekStart, weekEnd, fridayNight);
        return Optional.of(run);
    }

    /** 한 세그먼트의 귀가·동승 신청을 금요일 23:30 슬롯에 생성한다(모두 같은 셀로 집계되게). */
    private void seedSegment(Long university, Long cityId, Long pickupId, Long dongId, Segment s, Instant at) {
        for (int i = 0; i < s.journeys(); i++) {
            // 70% 탑승 확인, 15% 혼자 택시, 15% 막차 선택
            JourneyOutcome outcome = i % 20 < 14 ? JourneyOutcome.POOL_BOARDED
                    : i % 20 < 17 ? JourneyOutcome.SOLO_SELECTED : JourneyOutcome.BUS_SELECTED;
            journeys.saveAndFlush(Journey.builder().clientJourneyKey(UUID.randomUUID())
                    .userId(1L).universityId(university).pickupPointId(pickupId).destinationDongId(dongId)
                    .intendedDepartureAt(at).outcome(outcome).build());
        }
        for (int i = 0; i < s.requests(); i++) {
            RideRequestStatus status = i < s.failed() ? RideRequestStatus.FAILED : RideRequestStatus.MATCHED;
            rideRequests.saveAndFlush(RideRequest.builder().clientRequestKey(UUID.randomUUID())
                    .journeyId(1L).universityId(university).pickupPointId(pickupId).districtId(cityId)
                    .destinationDongId(dongId).departureAt(at).dropoffLatEncrypted("x").dropoffLngEncrypted("x")
                    .sameGenderOnly(true).genderSnapshot(Gender.FEMALE).status(status).build());
        }
    }

    private PickupPoint findOrCreatePickup(Long university, Long cityId, String name) {
        return pickups.findActiveByUniversityIdOrCommon(university).stream()
                .filter(p -> p.getName().equals(name) && cityId.equals(p.getAreaId())).findFirst()
                .orElseGet(() -> pickups.saveAndFlush(PickupPoint.builder()
                        .universityId(university).areaId(cityId).name(name).meetingNote("데모 거점")
                        .latitude(bd("37.3220")).longitude(bd("127.1260")).active(true).build()));
    }

    private AdministrativeArea area(String code, String name, AreaLevel level, Long parent, BigDecimal lat, BigDecimal lng) {
        return areas.findByOfficialCode(code).orElseGet(() -> areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode(code).name(name).level(level).parentId(parent).latitude(lat).longitude(lng).build()));
    }

    /** [start, end] 안의 금요일 밤 23:35(KST) 시각. 7일 창이라 금요일은 정확히 하나. */
    private Instant fridayNightWithin(LocalDate start, LocalDate end) {
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.FRIDAY) {
                return ZonedDateTime.of(d, java.time.LocalTime.of(23, 35), DemandAggregator.SERVICE_ZONE).toInstant();
            }
        }
        // 안전망: 창의 마지막 날 밤
        return ZonedDateTime.of(end, java.time.LocalTime.of(23, 35), DemandAggregator.SERVICE_ZONE).toInstant();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
