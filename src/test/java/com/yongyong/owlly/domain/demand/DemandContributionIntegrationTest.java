package com.yongyong.owlly.domain.demand;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import com.yongyong.owlly.domain.demand.repository.DemandEstimateRepository;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API-45 내 귀가 반영 횟수·후보 여부(Contribution)의 스펙 동작을 실제 PostgreSQL 로 검증한다.
 *
 * <p>검증 목적: 학생 본인만 접근, 산출 기간 안의 내 귀가만(그 거점→목적지로) 집계, 표본 미달·셀 없음이면
 * candidateId/remainingToCandidate 를 가린다(visibility 로 구분), 없는 산출은 409, 필수 파라미터 검증.
 */
@SpringBootTest(properties = {"owlly.matching.enabled=false"})
@AutoConfigureMockMvc
class DemandContributionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UniversityRepository universities;
    @Autowired UserRepository users;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired PickupPointRepository pickups;
    @Autowired DemandRunRepository runs;
    @Autowired DemandEstimateRepository estimates;
    @Autowired JourneyRepository journeys;

    Long university;
    User student, staff;
    AdministrativeArea city, dong, otherDong;
    PickupPoint pickup;
    DemandRun run;
    final LocalDate periodStart = LocalDate.of(2099, 1, 5), periodEnd = LocalDate.of(2099, 1, 11);
    final List<Long> runIds = new ArrayList<>(), estimateIds = new ArrayList<>(),
            areaIds = new ArrayList<>(), journeyIds = new ArrayList<>();

    @BeforeEach void setup() {
        university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        student = user(Role.STUDENT);
        staff = user(Role.STAFF);
        city = area("기여시", AreaLevel.CITY, null);
        dong = area("기여동", AreaLevel.DONG, city.getId());
        otherDong = area("다른동", AreaLevel.DONG, city.getId());
        pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(university).areaId(city.getId())
                .name("기여정문").meetingNote("정문").latitude(new BigDecimal("37.2")).longitude(new BigDecimal("127.05"))
                .active(true).build());
        run = runs.saveAndFlush(DemandRun.builder().periodStart(periodStart).periodEnd(periodEnd)
                .modelVersion("test-v1").inputSources("{}").status(DemandRunStatus.SUCCEEDED).build());
        runIds.add(run.getId());
    }

    @AfterEach void cleanup() {
        journeyIds.forEach(id -> jdbc.update("delete from journeys where id=?", id));
        estimateIds.forEach(id -> jdbc.update("delete from demand_estimates where id=?", id));
        runIds.forEach(id -> jdbc.update("delete from demand_runs where id=?", id));
        pickups.deleteById(pickup.getId());
        areaIds.forEach(id -> jdbc.update("delete from administrative_areas where id=?", id));
        users.deleteById(student.getId());
        users.deleteById(staff.getId());
    }

    @Test void studentSeesInPeriodCountAndVisibleCandidate() throws Exception {
        journey(student, dong, inPeriod());
        journey(student, dong, inPeriod());
        DemandEstimate candidate = estimate(dong, 1, false); // 공개 후보
        callGet(student, run.getId(), pickup.getId(), dong.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(String.valueOf(run.getId())))
                .andExpect(jsonPath("$.data.myJourneyCount").value(2))
                .andExpect(jsonPath("$.data.visibility").value("VISIBLE"))
                .andExpect(jsonPath("$.data.candidateId").value(String.valueOf(candidate.getId())));
    }

    @Test void suppressedCellHidesCandidateAndRemaining() throws Exception {
        journey(student, dong, inPeriod());
        estimate(dong, 1, true); // suppressed → 수치 가림
        callGet(student, run.getId(), pickup.getId(), dong.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myJourneyCount").value(1))
                .andExpect(jsonPath("$.data.visibility").value("INSUFFICIENT_SAMPLE"))
                .andExpect(jsonPath("$.data.candidateId").isEmpty())
                .andExpect(jsonPath("$.data.remainingToCandidate").isEmpty());
    }

    @Test void missingCellYieldsNotAvailable() throws Exception {
        journey(student, dong, inPeriod()); // 셀(estimate) 없음
        callGet(student, run.getId(), pickup.getId(), dong.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.data.candidateId").isEmpty());
    }

    @Test void onlyCountsMyInPeriodJourneysForThatSegment() throws Exception {
        journey(student, dong, inPeriod());                 // 집계 O
        journey(student, dong, outOfPeriod());              // 기간 밖 → 제외
        journey(student, otherDong, inPeriod());            // 다른 목적지 → 제외
        journey(staff, dong, inPeriod());                   // 남의 귀가 → 제외
        estimate(dong, 1, false);
        callGet(student, run.getId(), pickup.getId(), dong.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myJourneyCount").value(1));
    }

    @Test void missingRunIsConflict() throws Exception {
        callGet(student, 99999999L, pickup.getId(), dong.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEMAND_NOT_READY"));
    }

    @Test void staffForbiddenAndUnauthenticatedRejected() throws Exception {
        callGet(staff, run.getId(), pickup.getId(), dong.getId()).andExpect(status().isForbidden()); // /me/** 는 학생 전용
        mvc.perform(get(path(run.getId(), pickup.getId(), dong.getId()))).andExpect(status().isUnauthorized());
    }

    @Test void requiredParamsAreValidated() throws Exception {
        // 필수 누락
        mvc.perform(get("/api/v1/me/demand-contribution").with(authentication(auth(student))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        // 형식 오류(양의 정수 아님)
        mvc.perform(get("/api/v1/me/demand-contribution?runId=0&pickupPointId=" + pickup.getId()
                        + "&destinationDongId=" + dong.getId()).with(authentication(auth(student))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- helpers ---

    private Instant inPeriod() { return Instant.parse("2099-01-06T14:00:00Z"); }      // 기간 안
    private Instant outOfPeriod() { return Instant.parse("2099-02-01T14:00:00Z"); }    // 기간 밖

    private String path(Long runId, Long pickupId, Long dongId) {
        return "/api/v1/me/demand-contribution?runId=" + runId + "&pickupPointId=" + pickupId + "&destinationDongId=" + dongId;
    }

    private ResultActions callGet(User u, Long runId, Long pickupId, Long dongId) throws Exception {
        return mvc.perform(get(path(runId, pickupId, dongId)).with(authentication(auth(u))));
    }

    private UsernamePasswordAuthenticationToken auth(User u) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(u.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
    }

    private User user(Role role) {
        return users.saveAndFlush(User.builder().universityId(university)
                .email("contrib-" + UUID.randomUUID() + "@hufs.ac.kr").role(role).status(UserStatus.ACTIVE).build());
    }

    private AdministrativeArea area(String name, AreaLevel level, Long parent) {
        AdministrativeArea a = areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode("DC" + UUID.randomUUID().toString().substring(0, 8))
                .name(name).level(level).parentId(parent).build());
        areaIds.add(a.getId());
        return a;
    }

    private void journey(User u, AdministrativeArea destDong, Instant departAt) {
        Journey j = journeys.saveAndFlush(Journey.builder().clientJourneyKey(UUID.randomUUID())
                .userId(u.getId()).universityId(university).pickupPointId(pickup.getId())
                .destinationDongId(destDong.getId()).intendedDepartureAt(departAt).outcome(JourneyOutcome.POOL_BOARDED).build());
        journeyIds.add(j.getId());
    }

    private DemandEstimate estimate(AdministrativeArea destDong, Integer rank, boolean suppressed) {
        DemandEstimate e = estimates.saveAndFlush(DemandEstimate.builder()
                .runId(run.getId()).universityId(university).pickupPointId(pickup.getId()).destinationDongId(destDong.getId())
                .weekday(1).slotStart(LocalTime.of(23, 30))
                .journeyCount(10).failedJourneyCount(3).pooledJourneyCount(4).pooledRegisteredCount(8)
                .busSelectedCount(2).soloSelectedCount(1)
                .estimatedWeeklyDemand(new BigDecimal("40")).candidateThreshold(new BigDecimal("20"))
                .candidateRank(rank).evidence("[]").suppressed(suppressed).build());
        estimateIds.add(e.getId());
        return e;
    }
}
