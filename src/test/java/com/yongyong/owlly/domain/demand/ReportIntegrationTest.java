package com.yongyong.owlly.domain.demand;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.demand.dto.ReportFilters;
import com.yongyong.owlly.domain.demand.dto.ReportItemInput;
import com.yongyong.owlly.domain.demand.dto.StakeholderReportCreate;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import com.yongyong.owlly.domain.demand.entity.ReportAction;
import com.yongyong.owlly.domain.demand.entity.ReportFormat;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API-46 리포트 생성 / API-47 목록 / API-48 상세의 스펙 동작을 실제 PostgreSQL 로 검증한다.
 *
 * <p>검증 목적: STAFF 전용, 같은 산출·공개 후보만·필터 일치 검증(각 422 코드), 레이아웃 상한·중복 금지,
 * 후보 스냅샷·상단 집계 박제, 본인 리포트만 목록·상세(남의 것은 404), 커서 페이징, ETag.
 */
@SpringBootTest(properties = {"owlly.matching.enabled=false"})
@AutoConfigureMockMvc
class ReportIntegrationTest {

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
    User staffA, staffB, studentUser;
    AdministrativeArea city;
    PickupPoint pickup;
    DemandRun run, otherRun;
    DemandEstimate good1, good2, suppressedEst, wrongWeekday, otherRunEst;
    final LocalDate periodStart = LocalDate.of(2099, 1, 5), periodEnd = LocalDate.of(2099, 1, 11);
    final List<Long> runIds = new ArrayList<>(), estimateIds = new ArrayList<>(), areaIds = new ArrayList<>();

    @BeforeEach void setup() {
        university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        staffA = user(Role.STAFF);
        staffB = user(Role.STAFF);
        studentUser = user(Role.STUDENT);
        city = area("리포트시");
        pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(university).areaId(city.getId())
                .name("리포트정문").meetingNote("정문").latitude(new BigDecimal("37.2")).longitude(new BigDecimal("127.05"))
                .active(true).build());
        run = run(periodStart, periodEnd);
        otherRun = run(LocalDate.of(2099, 2, 1), LocalDate.of(2099, 2, 7));

        good1 = estimate(run, dong(), 4, LocalTime.of(23, 30), 1, false);
        good2 = estimate(run, dong(), 4, LocalTime.of(23, 30), 2, false);
        suppressedEst = estimate(run, dong(), 4, LocalTime.of(23, 30), 3, true);
        wrongWeekday = estimate(run, dong(), 5, LocalTime.of(23, 30), 4, false);
        otherRunEst = estimate(otherRun, dong(), 4, LocalTime.of(23, 30), 1, false);
    }

    @AfterEach void cleanup() {
        jdbc.update("delete from stakeholder_report_items where report_id in "
                + "(select id from stakeholder_reports where created_by in (?,?,?))", staffA.getId(), staffB.getId(), studentUser.getId());
        jdbc.update("delete from stakeholder_reports where created_by in (?,?,?)", staffA.getId(), staffB.getId(), studentUser.getId());
        estimateIds.forEach(id -> jdbc.update("delete from demand_estimates where id=?", id));
        runIds.forEach(id -> jdbc.update("delete from demand_runs where id=?", id));
        pickups.deleteById(pickup.getId());
        areaIds.forEach(id -> jdbc.update("delete from administrative_areas where id=?", id));
        users.deleteById(staffA.getId());
        users.deleteById(staffB.getId());
        users.deleteById(studentUser.getId());
    }

    // ---------- API-46 생성 ----------

    @Test void staffCreatesDraftWithSnapshotItemsAndSummary() throws Exception {
        var req = create(List.of(item(good1, ReportAction.CITY_PROPOSAL), item(good2, ReportAction.SHUTTLE_EXTENSION)), null);
        callPost(staffA, req, key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.format").value("PDF"))
                .andExpect(jsonPath("$.data.downloadAvailable").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data.items[0].estimateId").value(String.valueOf(good1.getId())))
                .andExpect(jsonPath("$.data.items[0].action").value("CITY_PROPOSAL"))
                // 리포트 표의 "기존 공급" 열 재료가 후보 스냅샷에 박제되는지
                .andExpect(jsonPath("$.data.items[0].candidate.existingSupply").value("N1 하루 1회"))
                .andExpect(jsonPath("$.data.items[0].candidate.pooledRegisteredCount").value(8))
                .andExpect(jsonPath("$.data.items[1].displayOrder").value(2))
                // 상단 집계는 필터와 같은 조건의 공개 셀 합(good1+good2=journey 20, failed 6) — suppressed/다른요일 제외
                .andExpect(jsonPath("$.data.summary.journeyCount").value(20))
                .andExpect(jsonPath("$.data.summary.failedJourneyCount").value(6));
    }

    @Test void mixedDemandRunRejected() throws Exception {
        callPost(staffA, create(List.of(item(good1, ReportAction.CITY_PROPOSAL), item(otherRunEst, ReportAction.CITY_PROPOSAL)), null), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MIXED_DEMAND_RUN"));
    }

    @Test void suppressedCandidateRejected() throws Exception {
        callPost(staffA, create(List.of(item(suppressedEst, ReportAction.CITY_PROPOSAL)), null), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SUPPRESSED_CANDIDATE"));
    }

    @Test void filterMismatchByWeekdayRejected() throws Exception {
        callPost(staffA, create(List.of(item(wrongWeekday, ReportAction.CITY_PROPOSAL)), null), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REPORT_FILTER_MISMATCH"));
    }

    @Test void filterPeriodMismatchRejected() throws Exception {
        // 필터 기간이 산출 기간과 다르면 거부
        var req = new StakeholderReportCreate(String.valueOf(run.getId()), ReportFormat.PDF,
                new ReportFilters(LocalDate.of(2099, 1, 4), periodEnd, 4, "23:30"),
                List.of(item(good1, ReportAction.CITY_PROPOSAL)), null);
        callPost(staffA, req, key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REPORT_FILTER_MISMATCH"));
    }

    @Test void layoutLimitRejected() throws Exception {
        List<ReportItemInput> many = new ArrayList<>();
        for (int i = 1; i <= 11; i++) many.add(new ReportItemInput(String.valueOf(i), ReportAction.CITY_PROPOSAL));
        callPost(staffA, create(many, null), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REPORT_LAYOUT_LIMIT"));
    }

    @Test void duplicateCandidatesRejected() throws Exception {
        callPost(staffA, create(List.of(item(good1, ReportAction.CITY_PROPOSAL), item(good1, ReportAction.SHUTTLE_EXTENSION)), null), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test void studentForbiddenAndUnauthenticatedRejected() throws Exception {
        var req = create(List.of(item(good1, ReportAction.CITY_PROPOSAL)), null);
        callPost(studentUser, req, key()).andExpect(status().isForbidden()); // /staff/** 는 STAFF 전용
        mvc.perform(post("/api/v1/staff/reports").with(csrf().asHeader()).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test void idempotencyKeyMustBePresentAndUuid() throws Exception {
        var req = create(List.of(item(good1, ReportAction.CITY_PROPOSAL)), null);
        // 누락
        mvc.perform(post("/api/v1/staff/reports").with(authentication(auth(staffA))).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        // UUID 아님
        callPost(staffA, req, "not-a-uuid").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- API-47 목록 ----------

    @Test void listsOnlyOwnReportsNewestFirst() throws Exception {
        long a1 = createdId(staffA);
        long a2 = createdId(staffA);
        long b1 = createdId(staffB);
        mvc.perform(get("/api/v1/staff/reports").with(authentication(auth(staffA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(a2))) // 최신순
                .andExpect(jsonPath("$.data[1].id").value(String.valueOf(a1)));
        mvc.perform(get("/api/v1/staff/reports").with(authentication(auth(staffB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(b1)));
    }

    @Test void listPaginatesWithCursor() throws Exception {
        createdId(staffA); createdId(staffA); createdId(staffA);
        var first = mvc.perform(get("/api/v1/staff/reports?limit=2").with(authentication(auth(staffA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(true)).andReturn();
        String cursor = mapper.readTree(first.getResponse().getContentAsString()).at("/meta/nextCursor").asText();
        mvc.perform(get("/api/v1/staff/reports?limit=2&cursor=" + cursor).with(authentication(auth(staffA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    // ---------- API-48 상세 ----------

    @Test void getOwnReportReturnsDataAndEtag() throws Exception {
        long id = createdId(staffA);
        mvc.perform(get("/api/v1/staff/reports/" + id).with(authentication(auth(staffA))))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.data.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test void otherOwnersOrMissingReportIsNotFound() throws Exception {
        long id = createdId(staffA);
        mvc.perform(get("/api/v1/staff/reports/" + id).with(authentication(auth(staffB))))
                .andExpect(status().isNotFound()); // 남이 만든 리포트는 존재 여부를 노출하지 않는다
        mvc.perform(get("/api/v1/staff/reports/99999999").with(authentication(auth(staffA))))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private StakeholderReportCreate create(List<ReportItemInput> items, String narrative) {
        return new StakeholderReportCreate(String.valueOf(run.getId()), ReportFormat.PDF,
                new ReportFilters(periodStart, periodEnd, 4, "23:30"), items, narrative);
    }

    private ReportItemInput item(DemandEstimate e, ReportAction action) {
        return new ReportItemInput(String.valueOf(e.getId()), action);
    }

    private long createdId(User staff) throws Exception {
        var res = callPost(staff, create(List.of(item(good1, ReportAction.CITY_PROPOSAL)), null), key())
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private ResultActions callPost(User staff, StakeholderReportCreate req, String key) throws Exception {
        return mvc.perform(post("/api/v1/staff/reports").with(authentication(auth(staff))).with(csrf().asHeader())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)));
    }

    private String key() { return UUID.randomUUID().toString(); }

    private UsernamePasswordAuthenticationToken auth(User u) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(u.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
    }

    private User user(Role role) {
        return users.saveAndFlush(User.builder().universityId(university)
                .email("report-" + UUID.randomUUID() + "@hufs.ac.kr").role(role).status(UserStatus.ACTIVE).build());
    }

    private AdministrativeArea area(String name) {
        AdministrativeArea a = areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode("RP" + UUID.randomUUID().toString().substring(0, 8))
                .name(name).level(AreaLevel.CITY).build());
        areaIds.add(a.getId());
        return a;
    }

    private AdministrativeArea dong() {
        AdministrativeArea a = areas.saveAndFlush(AdministrativeArea.builder()
                .officialCode("RD" + UUID.randomUUID().toString().substring(0, 8))
                .name("리포트동").level(AreaLevel.DONG).parentId(city.getId())
                .latitude(new BigDecimal("37.3")).longitude(new BigDecimal("127.1")).build());
        areaIds.add(a.getId());
        return a;
    }

    private DemandRun run(LocalDate start, LocalDate end) {
        DemandRun r = runs.saveAndFlush(DemandRun.builder().periodStart(start).periodEnd(end)
                .modelVersion("test-v1").inputSources("{}").status(DemandRunStatus.SUCCEEDED).build());
        runIds.add(r.getId());
        return r;
    }

    private DemandEstimate estimate(DemandRun r, AdministrativeArea destDong, int weekday, LocalTime slot,
                                    Integer rank, boolean suppressed) {
        DemandEstimate e = estimates.saveAndFlush(DemandEstimate.builder()
                .runId(r.getId()).universityId(university).pickupPointId(pickup.getId()).destinationDongId(destDong.getId())
                .weekday(weekday).slotStart(slot)
                .journeyCount(10).failedJourneyCount(3).pooledJourneyCount(4).pooledRegisteredCount(8)
                .busSelectedCount(2).soloSelectedCount(1)
                .estimatedWeeklyDemand(new BigDecimal("40")).candidateThreshold(new BigDecimal("20"))
                .candidateRank(rank).existingSupply("N1 하루 1회").evidence("[]").suppressed(suppressed).build());
        estimateIds.add(e.getId());
        return e;
    }
}
