package com.yongyong.owlly.domain.demand.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.demand.dto.DemandCandidate;
import com.yongyong.owlly.domain.demand.dto.DemandSummary;
import com.yongyong.owlly.domain.demand.dto.ReportFilters;
import com.yongyong.owlly.domain.demand.dto.ReportItem;
import com.yongyong.owlly.domain.demand.dto.ReportItemInput;
import com.yongyong.owlly.domain.demand.dto.StakeholderReportCreate;
import com.yongyong.owlly.domain.demand.dto.StakeholderReportResponse;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.StakeholderReport;
import com.yongyong.owlly.domain.demand.entity.StakeholderReportItem;
import com.yongyong.owlly.domain.demand.entity.StakeholderReportStatus;
import com.yongyong.owlly.domain.demand.repository.DemandEstimateRepository;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
import com.yongyong.owlly.domain.demand.repository.StakeholderReportItemRepository;
import com.yongyong.owlly.domain.demand.repository.StakeholderReportRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관계자 리포트 생성·조회 (API-46·47·48). 스태프만(경로 보안으로 강제).
 *
 * <p>생성 시 요청으로는 어느 산출·어떤 조건·어떤 후보·조치만 받고, 수요·순위·근거 같은 수치는 서버가 산출 결과에서
 * 읽어 candidate_snapshot 으로 박제한다. 이후 산출이 바뀌어도 리포트는 만든 시점 값을 유지한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    /** 한 리포트에 담을 수 있는 후보 수 상한(레이아웃 정책 미확정, 잠정값). */
    private static final int MAX_ITEMS = 10;

    private final StakeholderReportRepository reports;
    private final StakeholderReportItemRepository reportItems;
    private final DemandRunRepository demandRuns;
    private final DemandEstimateRepository demandEstimates;
    private final DemandCandidateAssembler candidateAssembler;
    private final ObjectMapper objectMapper;

    /** 목록 한 페이지(커서 페이징). */
    public record ListResult(List<StakeholderReportResponse> reports, String nextCursor, boolean hasMore) {}

    /** API-46 리포트 초안 생성. 산출·필터·후보 검증 후 후보 스냅샷을 박제해 저장한다. */
    @Transactional
    public StakeholderReportResponse create(Long userId, StakeholderReportCreate request) {
        Long runId = Long.valueOf(request.runId());
        DemandRun run = demandRuns.findById(runId)
                .orElseThrow(() -> new OwllyException(ErrorCode.DEMAND_NOT_READY));
        requireFilterMatchesRun(request.filters(), run);

        List<Long> estimateIds = request.items().stream().map(i -> Long.valueOf(i.estimateId())).toList();
        if (estimateIds.size() > MAX_ITEMS) {
            throw new OwllyException(ErrorCode.REPORT_LAYOUT_LIMIT);
        }
        if (estimateIds.stream().distinct().count() != estimateIds.size()) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR, List.of("items: 같은 후보를 중복해서 담을 수 없습니다."));
        }

        Map<Long, DemandEstimate> estimates = demandEstimates.findAllById(estimateIds).stream()
                .collect(Collectors.toMap(DemandEstimate::getId, e -> e));
        validateCandidates(estimateIds, estimates, runId, request.filters());

        DemandSummary summary = summarize(runId, request.filters());
        StakeholderReport report = reports.save(StakeholderReport.builder()
                .createdBy(userId)
                .runId(runId)
                .filtersSnapshot(writeJson(request.filters()))
                .summarySnapshot(writeJson(summary))
                .narrative(request.narrative())
                .format(request.format())
                .status(StakeholderReportStatus.DRAFT)
                .build());

        List<ReportItem> responseItems = saveItems(report.getId(), request.items(), estimates);
        return StakeholderReportResponse.of(report, request.filters(), summary, responseItems);
    }

    /** 리포트 필터와 같은 조건으로 상단 집계(귀가·매칭실패·혼자택시·막차 합계)를 계산한다. */
    private DemandSummary summarize(Long runId, ReportFilters filters) {
        LocalTime slot = filters.slotStart() == null ? null : LocalTime.parse(filters.slotStart());
        Object[] row = demandEstimates.summarize(runId, filters.weekday(), slot).get(0);
        return new DemandSummary(
                ((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(),
                ((Number) row[2]).intValue(),
                ((Number) row[3]).intValue());
    }

    /** API-47 내 리포트 목록(최신순, 커서 페이징). */
    public ListResult list(Long userId, String cursor, int limit) {
        PageRequest pageable = PageRequest.of(0, limit + 1);
        List<StakeholderReport> rows = cursor == null
                ? reports.findByCreatedByOrderByIdDesc(userId, pageable)
                : reports.findByCreatedByAndIdLessThanOrderByIdDesc(
                        userId, ReportCursor.decode(cursor).id(), pageable);

        boolean hasMore = rows.size() > limit;
        List<StakeholderReport> page = hasMore ? rows.subList(0, limit) : rows;

        Map<Long, List<StakeholderReportItem>> itemsByReport = page.isEmpty() ? Map.of()
                : reportItems.findByReportIdInOrderByReportIdAscDisplayOrderAsc(
                        page.stream().map(StakeholderReport::getId).toList()).stream()
                .collect(Collectors.groupingBy(StakeholderReportItem::getReportId));

        List<StakeholderReportResponse> responses = page.stream()
                .map(r -> StakeholderReportResponse.of(
                        r, readFilters(r.getFiltersSnapshot()), readSummary(r.getSummarySnapshot()),
                        toResponseItems(itemsByReport.getOrDefault(r.getId(), List.of()))))
                .toList();
        String nextCursor = hasMore ? ReportCursor.encode(page.get(page.size() - 1)) : null;
        return new ListResult(responses, nextCursor, hasMore);
    }

    /** API-48 리포트 상세. 내가 만든 리포트가 아니면 404(존재 여부를 노출하지 않는다). */
    public StakeholderReportResponse get(Long userId, Long reportId) {
        StakeholderReport report = reports.findById(reportId)
                .filter(r -> r.getCreatedBy().equals(userId))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        List<ReportItem> items = toResponseItems(
                reportItems.findByReportIdOrderByDisplayOrderAsc(reportId));
        return StakeholderReportResponse.of(
                report, readFilters(report.getFiltersSnapshot()), readSummary(report.getSummarySnapshot()), items);
    }

    /** 선택한 후보들이 같은 산출·공개 상태·필터 조건을 만족하는지 검증한다. */
    private void validateCandidates(
            List<Long> estimateIds, Map<Long, DemandEstimate> estimates, Long runId, ReportFilters filters) {
        LocalTime slot = filters.slotStart() == null ? null : LocalTime.parse(filters.slotStart());
        for (Long id : estimateIds) {
            DemandEstimate e = estimates.get(id);
            if (e == null) {
                throw new OwllyException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!e.getRunId().equals(runId)) {
                throw new OwllyException(ErrorCode.MIXED_DEMAND_RUN);
            }
            if (e.isSuppressed()) {
                throw new OwllyException(ErrorCode.SUPPRESSED_CANDIDATE);
            }
            if ((filters.weekday() != null && e.getWeekday() != filters.weekday())
                    || (slot != null && !slot.equals(e.getSlotStart()))) {
                throw new OwllyException(ErrorCode.REPORT_FILTER_MISMATCH);
            }
        }
    }

    /** 리포트 필터의 기간이 산출 기간과 일치해야 한다(다른 기간 산출 혼용 방지). */
    private void requireFilterMatchesRun(ReportFilters filters, DemandRun run) {
        if (!filters.periodStart().equals(run.getPeriodStart())
                || !filters.periodEnd().equals(run.getPeriodEnd())) {
            throw new OwllyException(ErrorCode.REPORT_FILTER_MISMATCH);
        }
    }

    /** 후보마다 현재 산출값으로 DemandCandidate 를 조립해 스냅샷으로 박제하고 저장한다(요청 순서 = displayOrder). */
    private List<ReportItem> saveItems(
            Long reportId, List<ReportItemInput> inputs, Map<Long, DemandEstimate> estimates) {
        List<ReportItem> result = new java.util.ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            ReportItemInput input = inputs.get(i);
            DemandEstimate estimate = estimates.get(Long.valueOf(input.estimateId()));
            DemandCandidate candidate = candidateAssembler.assemble(List.of(estimate)).get(0);
            int displayOrder = i + 1;
            reportItems.save(StakeholderReportItem.builder()
                    .reportId(reportId)
                    .estimateId(estimate.getId())
                    .action(input.action())
                    .displayOrder(displayOrder)
                    .candidateSnapshot(writeJson(candidate))
                    .build());
            result.add(new ReportItem(input.estimateId(), input.action().name(), displayOrder, candidate));
        }
        return result;
    }

    /** 저장된 후보 항목을 스냅샷에서 복원해 응답용으로 바꾼다. */
    private List<ReportItem> toResponseItems(List<StakeholderReportItem> items) {
        return items.stream()
                .map(it -> new ReportItem(
                        String.valueOf(it.getEstimateId()),
                        it.getAction().name(),
                        it.getDisplayOrder(),
                        readCandidate(it.getCandidateSnapshot())))
                .toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new OwllyException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private ReportFilters readFilters(String json) {
        try {
            return objectMapper.readValue(json, ReportFilters.class);
        } catch (Exception e) {
            throw new OwllyException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 박제된 summary 복원. 과거 리포트 등으로 비어 있으면 null. */
    private DemandSummary readSummary(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DemandSummary.class);
        } catch (Exception e) {
            throw new OwllyException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private DemandCandidate readCandidate(String json) {
        try {
            return objectMapper.readValue(json, DemandCandidate.class);
        } catch (Exception e) {
            throw new OwllyException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
