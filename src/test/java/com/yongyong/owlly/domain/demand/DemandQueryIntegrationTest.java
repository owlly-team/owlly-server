package com.yongyong.owlly.domain.demand;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import com.yongyong.owlly.domain.demand.repository.DemandEstimateRepository;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
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

/**
 * API-43 수요 지도·노선 후보 목록 / API-44 상세의 스펙 동작을 실제 PostgreSQL 로 검증한다.
 *
 * <p>검증 목적(통과가 아니라 명세 준수): 로그인 사용자 누구나 접근, 표본 미달(suppressed) 제외,
 * 후보 순위 오름차순·비후보 뒤·동률 id 오름차순 정렬, 커서 페이징 무결성, runId/기간 해석과 409,
 * 지도 좌표(거점·동 중심) 동봉, 상세의 숨김/없음 404.
 *
 * <p>로컬/CI DB 에 이미 수요 산출 데이터가 있을 수 있어, 데이터 비교 테스트는 runId 를 고정하고
 * "최신 산출 해석"은 fixture run 을 먼 미래(2099) 기간으로 둬 전역 최신이 되도록 한다.
 */
@SpringBootTest(properties = {"owlly.matching.enabled=false"})
@AutoConfigureMockMvc
class DemandQueryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UniversityRepository universities;
    @Autowired UserRepository users;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired PickupPointRepository pickups;
    @Autowired DemandRunRepository runs;
    @Autowired DemandEstimateRepository estimates;

    Long university;
    User student, staff;
    AdministrativeArea city, dongA, dongB, dongC, dongD, dongNoCoords;
    PickupPoint pickup;
    DemandRun run, olderRun, runningRun;
    // 먼 미래라 로컬/CI 의 실제 산출 데이터보다 항상 최신(기간 종료일 내림차순 해석에서 이김).
    final LocalDate periodStart = LocalDate.of(2099, 1, 5), periodEnd = LocalDate.of(2099, 1, 11);
    // 같은 요일·슬롯 안에서 셀을 구분하려면 동이 달라야 한다(uk_demand_estimate_cell).
    // e1(rank1) e2(rank2) e3(비후보) e4(suppressed) e5(다른요일) e6(동좌표없음, rank4)
    DemandEstimate e1, e2, e3, e4, e5, e6;
    final List<Long> runIds = new ArrayList<>(), estimateIds = new ArrayList<>(), areaIds = new ArrayList<>();

    @BeforeEach void setup() {
        university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        student = user(Role.STUDENT);
        staff = user(Role.STAFF);
        city = area("수요시", AreaLevel.CITY, null, null, null);
        dongA = area("수요동A", AreaLevel.DONG, city.getId(), new BigDecimal("37.30000"), new BigDecimal("127.10000"));
        dongB = area("수요동B", AreaLevel.DONG, city.getId(), new BigDecimal("37.31000"), new BigDecimal("127.11000"));
        dongC = area("수요동C", AreaLevel.DONG, city.getId(), new BigDecimal("37.32000"), new BigDecimal("127.12000"));
        dongD = area("수요동D", AreaLevel.DONG, city.getId(), new BigDecimal("37.33000"), new BigDecimal("127.13000"));
        dongNoCoords = area("좌표없는동", AreaLevel.DONG, city.getId(), null, null);
        pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(university).areaId(city.getId())
                .name("수요정문").meetingNote("정문").latitude(new BigDecimal("37.20000")).longitude(new BigDecimal("127.05000"))
                .active(true).build());

        run = run(periodStart, periodEnd, DemandRunStatus.SUCCEEDED);
        olderRun = run(periodStart.minusDays(7), periodEnd.minusDays(7), DemandRunStatus.SUCCEEDED);
        runningRun = run(periodStart, periodEnd, DemandRunStatus.RUNNING);

        e1 = estimate(run.getId(), dongA.getId(), 4, LocalTime.of(23, 30), 1, false, "40");
        e2 = estimate(run.getId(), dongB.getId(), 4, LocalTime.of(23, 30), 2, false, "30");
        e3 = estimate(run.getId(), dongC.getId(), 4, LocalTime.of(23, 30), null, false, "10"); // 비후보
        e4 = estimate(run.getId(), dongD.getId(), 4, LocalTime.of(23, 30), 3, true, "99");      // suppressed → 숨김
        e5 = estimate(run.getId(), dongA.getId(), 5, LocalTime.of(0, 0), 1, false, "50");       // 다른 요일/슬롯
        e6 = estimate(run.getId(), dongNoCoords.getId(), 4, LocalTime.of(23, 30), 4, false, "5");
    }

    @AfterEach void cleanup() {
        estimateIds.forEach(id -> jdbc.update("delete from demand_estimates where id=?", id));
        runIds.forEach(id -> jdbc.update("delete from demand_runs where id=?", id));
        pickups.deleteById(pickup.getId());
        areaIds.forEach(id -> jdbc.update("delete from administrative_areas where id=?", id));
        users.deleteById(student.getId());
        users.deleteById(staff.getId());
    }

    @Test void anyLoggedInUserListsCandidatesRankedSuppressedExcludedNonCandidateLast() throws Exception {
        for (User u : List.of(student, staff)) { // 로그인 사용자면 학생·스태프 모두 접근 가능
            JsonNode body = getList(u, pinned("&weekday=4&slotStart=23:30"));
            List<Long> ids = ids(body.path("data"));
            // 후보 순위 오름차순(e1<e2) → 비후보(e3, e6 은 rank 있으니 e6 먼저) 뒤로, e4(suppressed) 제외
            assertThat(ids).containsExactly(e1.getId(), e2.getId(), e6.getId(), e3.getId());
            assertThat(ids).doesNotContain(e4.getId());
            assertThat(body.path("meta").path("runId").asText()).isEqualTo(String.valueOf(run.getId()));
        }
    }

    @Test void unauthenticatedListIsRejected() throws Exception {
        mvc.perform(get("/api/v1/demand/candidates")).andExpect(status().isUnauthorized());
    }

    @Test void candidateOnlyExcludesNonCandidateCells() throws Exception {
        List<Long> ids = ids(getList(student, pinned("&weekday=4&slotStart=23:30&candidateOnly=true")).path("data"));
        assertThat(ids).containsExactly(e1.getId(), e2.getId(), e6.getId()); // rank 있는 셀만, e3(비후보) 제외
    }

    @Test void weekdayAndSlotFilterNarrowCells() throws Exception {
        List<Long> ids = ids(getList(student, pinned("&weekday=5&slotStart=00:00")).path("data"));
        assertThat(ids).containsExactly(e5.getId());
    }

    @Test void cursorPaginatesWithoutSkipsOrDuplicates() throws Exception {
        List<Long> collected = new ArrayList<>();
        String query = pinned("&weekday=4&slotStart=23:30&limit=1");
        for (int guard = 0; guard < 10; guard++) {
            JsonNode body = getList(student, query);
            collected.addAll(ids(body.path("data")));
            JsonNode next = body.path("meta").path("nextCursor");
            if (next.isNull() || next.asText().isEmpty()) break;
            query = pinned("&weekday=4&slotStart=23:30&limit=1&cursor=" + next.asText());
        }
        assertThat(collected).containsExactly(e1.getId(), e2.getId(), e6.getId(), e3.getId());
        assertThat(collected).doesNotHaveDuplicates();
    }

    @Test void runIdSelectsThatRunAndRejectsNonSucceededOrMissing() throws Exception {
        assertThat(getList(student, "?runId=" + run.getId()).path("meta").path("runId").asText())
                .isEqualTo(String.valueOf(run.getId()));
        callGet(student, "?runId=" + runningRun.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEMAND_NOT_READY"));
        callGet(student, "?runId=99999999").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEMAND_NOT_READY"));
    }

    @Test void periodFilterMatchesRunAndDoesNotFallBackToLatest() throws Exception {
        assertThat(getList(student, "?periodStart=2099-01-05&periodEnd=2099-01-11").path("meta").path("runId").asText())
                .isEqualTo(String.valueOf(run.getId()));
        // 산출이 없는 기간은 최신으로 대체하지 않고 409
        callGet(student, "?periodStart=2000-01-01&periodEnd=2000-01-07").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEMAND_NOT_READY"));
    }

    @Test void noFilterResolvesToLatestSuccessfulRun() throws Exception {
        // run(2099-01-11 종료)이 전역에서 가장 최신 성공 산출 → 기본 해석은 run
        assertThat(getList(student, "").path("meta").path("runId").asText())
                .isEqualTo(String.valueOf(run.getId()));
    }

    @Test void mapCoordinatesIncludePickupAndDongCenterNullWhenUnset() throws Exception {
        JsonNode data = getList(student, pinned("&weekday=4&slotStart=23:30")).path("data");
        JsonNode first = node(data, e1.getId());
        assertThat(first.path("pickupLatitude").decimalValue()).isEqualByComparingTo("37.20000");
        assertThat(first.path("destinationLatitude").decimalValue()).isEqualByComparingTo("37.30000");
        JsonNode noCoord = node(data, e6.getId()); // 동 좌표 미설정이면 null
        assertThat(noCoord.path("destinationLatitude").isNull()).isTrue();
        assertThat(noCoord.path("destinationLongitude").isNull()).isTrue();
    }

    @Test void candidateDetailReturnsCellForAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/v1/demand/candidates/" + e1.getId()).with(authentication(auth(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(e1.getId())))
                .andExpect(jsonPath("$.data.candidateRank").value(1))
                // 동근님 답변 반영: 동승 등록(실패율 분모)·기존 공급 필드 노출
                .andExpect(jsonPath("$.data.pooledRegisteredCount").value(8))
                .andExpect(jsonPath("$.data.existingSupply").value("N1 하루 1회"));
    }

    @Test void suppressedOrMissingDetailIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/demand/candidates/" + e4.getId()).with(authentication(auth(student))))
                .andExpect(status().isNotFound()); // 숨김 셀은 존재 여부를 노출하지 않는다
        mvc.perform(get("/api/v1/demand/candidates/99999999").with(authentication(auth(student))))
                .andExpect(status().isNotFound());
    }

    @Test void unauthenticatedDetailIsRejected() throws Exception {
        mvc.perform(get("/api/v1/demand/candidates/" + e1.getId())).andExpect(status().isUnauthorized());
    }

    @Test void otherUniversityDetailIsNotFound() throws Exception {
        Long otherUniversity = university == 1L ? 2L : 1L; // 조회자(한국외국어대)와 다른 학교
        DemandEstimate foreign = estimates.saveAndFlush(DemandEstimate.builder()
                .runId(run.getId()).universityId(otherUniversity).pickupPointId(pickup.getId()).destinationDongId(dongA.getId())
                .weekday(6).slotStart(LocalTime.of(1, 0))
                .journeyCount(10).failedJourneyCount(3).pooledJourneyCount(4).busSelectedCount(2).soloSelectedCount(1)
                .estimatedWeeklyDemand(new BigDecimal("40")).candidateThreshold(new BigDecimal("20"))
                .candidateRank(1).evidence("[]").suppressed(false).build());
        estimateIds.add(foreign.getId());
        // 다른 학교 셀은 숨김/없음과 동일하게 404(존재 여부·소속 학교를 노출하지 않는다)
        mvc.perform(get("/api/v1/demand/candidates/" + foreign.getId()).with(authentication(auth(student))))
                .andExpect(status().isNotFound());
    }

    @Test void runIdWithMismatchedPeriodIsConflict() throws Exception {
        // runId 는 존재하지만 함께 온 기간이 그 산출(2099-01-05~11)과 다르면 409
        callGet(student, "?runId=" + run.getId() + "&periodStart=2099-01-04&periodEnd=2099-01-11")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEMAND_NOT_READY"));
    }

    // --- helpers ---

    /** runId 를 fixture run 으로 고정한 쿼리스트링(뒤에 &필터 를 잇는다). */
    private String pinned(String extra) {
        return "?runId=" + run.getId() + extra;
    }

    private JsonNode getList(User u, String query) throws Exception {
        String json = callGet(u, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private org.springframework.test.web.servlet.ResultActions callGet(User u, String query) throws Exception {
        return mvc.perform(get("/api/v1/demand/candidates" + query).with(authentication(auth(u))));
    }

    private List<Long> ids(JsonNode dataArray) {
        List<Long> out = new ArrayList<>();
        dataArray.forEach(n -> out.add(n.path("id").asLong()));
        return out;
    }

    private JsonNode node(JsonNode dataArray, Long id) {
        for (JsonNode n : dataArray) if (n.path("id").asLong() == id) return n;
        throw new AssertionError("id " + id + " not in response");
    }

    private UsernamePasswordAuthenticationToken auth(User u) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(u.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
    }

    private User user(Role role) {
        return users.saveAndFlush(User.builder().universityId(university)
                .email("demand-" + UUID.randomUUID() + "@hufs.ac.kr").role(role).status(UserStatus.ACTIVE).build());
    }

    private AdministrativeArea area(String name, AreaLevel level, Long parent, BigDecimal lat, BigDecimal lng) {
        AdministrativeArea a = areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode("DQ" + UUID.randomUUID().toString().substring(0, 8))
                .name(name).level(level).parentId(parent).latitude(lat).longitude(lng).build());
        areaIds.add(a.getId());
        return a;
    }

    private DemandRun run(LocalDate start, LocalDate end, DemandRunStatus status) {
        DemandRun r = runs.saveAndFlush(DemandRun.builder().periodStart(start).periodEnd(end)
                .modelVersion("test-v1").inputSources("{}").status(status).build());
        runIds.add(r.getId());
        return r;
    }

    private DemandEstimate estimate(Long runId, Long dongId, int weekday, LocalTime slot,
                                    Integer rank, boolean suppressed, String weeklyDemand) {
        DemandEstimate e = estimates.saveAndFlush(DemandEstimate.builder()
                .runId(runId).universityId(university).pickupPointId(pickup.getId()).destinationDongId(dongId)
                .weekday(weekday).slotStart(slot)
                .journeyCount(10).failedJourneyCount(3).pooledJourneyCount(4).pooledRegisteredCount(8)
                .busSelectedCount(2).soloSelectedCount(1)
                .estimatedWeeklyDemand(new BigDecimal(weeklyDemand)).candidateThreshold(new BigDecimal("20"))
                .candidateRank(rank).existingSupply("N1 하루 1회").evidence("[]").suppressed(suppressed).build());
        estimateIds.add(e.getId());
        return e;
    }
}
