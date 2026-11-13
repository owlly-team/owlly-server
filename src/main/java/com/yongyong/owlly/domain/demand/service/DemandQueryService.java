package com.yongyong.owlly.domain.demand.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.demand.dto.DemandCandidate;
import com.yongyong.owlly.domain.demand.dto.DemandCandidatePage;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import com.yongyong.owlly.domain.demand.repository.DemandEstimateRepository;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수요 지도·노선 후보 조회 (API-43 목록, API-44 상세). 로그인 사용자.
 *
 * <p>추정 수요·후보 순위·근거 지표는 AI 수요 산출(run)의 결과이고, 이 서비스는 저장된 값을 필터·정렬·조인해 보여 줄 뿐
 * 점수를 다시 매기지 않는다. 최소 표본 미달 셀(is_suppressed)은 목록에서 빠진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandQueryService {

    private final DemandRunRepository demandRuns;
    private final DemandEstimateRepository demandEstimates;
    private final DemandCandidateAssembler candidateAssembler;

    /** 목록 조회 조건. 모두 선택값(cursor/limit 제외). */
    public record Query(
            Long runId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer weekday,
            LocalTime slotStart,
            boolean candidateOnly,
            String cursor,
            int limit) {}

    /** API-43 노선 후보 목록(후보 순위순, 표본 미달 제외, 커서 페이징). */
    public DemandCandidatePage getCandidates(Query query) {
        DemandRun run = resolveRun(query.runId(), query.periodStart(), query.periodEnd());

        Integer curRank = null;
        Long curId = null;
        if (query.cursor() != null) {
            DemandCursor cursor = DemandCursor.decode(query.cursor());
            curRank = cursor.effectiveRank();
            curId = cursor.id();
        }

        List<DemandEstimate> rows = demandEstimates.findPage(
                run.getId(), query.weekday(), query.slotStart(), query.candidateOnly(),
                curRank, curId, query.limit() + 1);

        boolean hasMore = rows.size() > query.limit();
        List<DemandEstimate> page = hasMore ? rows.subList(0, query.limit()) : rows;

        List<DemandCandidate> candidates = candidateAssembler.assemble(page);
        String nextCursor = hasMore ? DemandCursor.encode(page.get(page.size() - 1)) : null;
        return DemandCandidatePage.of(
                candidates, String.valueOf(run.getId()), run.getModelVersion(), hasMore, nextCursor);
    }

    /** API-44 노선 후보 상세. 없거나 최소 표본 미달(숨김)이면 404(존재 여부를 노출하지 않는다). */
    public DemandCandidate getCandidate(Long estimateId) {
        DemandEstimate estimate = demandEstimates.findById(estimateId)
                .filter(e -> !e.isSuppressed())
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        return candidateAssembler.assemble(List.of(estimate)).get(0);
    }

    /** runId 지정 시 그 산출, 아니면 (기간이 오면 그 기간의) 최신 성공 산출. 없으면 409 DEMAND_NOT_READY. */
    private DemandRun resolveRun(Long runId, LocalDate periodStart, LocalDate periodEnd) {
        if (runId != null) {
            return demandRuns.findById(runId)
                    .filter(r -> r.getStatus() == DemandRunStatus.SUCCEEDED)
                    .orElseThrow(() -> new OwllyException(ErrorCode.DEMAND_NOT_READY));
        }
        if (periodStart != null && periodEnd != null) {
            return demandRuns
                    .findFirstByStatusAndPeriodStartAndPeriodEndOrderByIdDesc(
                            DemandRunStatus.SUCCEEDED, periodStart, periodEnd)
                    .orElseThrow(() -> new OwllyException(ErrorCode.DEMAND_NOT_READY));
        }
        return demandRuns.findFirstByStatusOrderByPeriodEndDescIdDesc(DemandRunStatus.SUCCEEDED)
                .orElseThrow(() -> new OwllyException(ErrorCode.DEMAND_NOT_READY));
    }
}
