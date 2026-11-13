package com.yongyong.owlly.domain.demand.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.demand.dto.Contribution;
import com.yongyong.owlly.domain.demand.dto.Contribution.Visibility;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.repository.DemandEstimateRepository;
import com.yongyong.owlly.domain.demand.repository.DemandRunRepository;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 귀가가 특정 노선 수요에 얼마나 반영됐는지(API-45). 학생 본인만.
 *
 * <p>myJourneyCount 는 산출 기간 동안 내가 그 거점→목적지로 남긴 귀가 수를 journeys 에서 직접 센다.
 * 셀 노출 수준(visibility)·후보 여부(candidateId)는 해당 거점→목적지의 수요 셀로 판정한다.
 * remainingToCandidate(후보까지 남은 수요)는 산정 정책이 확정되기 전까지 null 이다 — 정책이 서면 이 메서드만 손본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandContributionService {

    /** 수요 집계의 서비스 현지 시간대(기간 경계를 자정으로 끊을 때 쓴다). */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DemandRunRepository demandRuns;
    private final DemandEstimateRepository demandEstimates;
    private final JourneyRepository journeys;
    private final UserRepository users;

    public Contribution getMyContribution(
            Long userId, Long runId, Long pickupPointId, Long destinationDongId) {
        requireStudent(userId);
        DemandRun run = demandRuns.findById(runId)
                .orElseThrow(() -> new OwllyException(ErrorCode.DEMAND_NOT_READY));

        Instant startInclusive = run.getPeriodStart().atStartOfDay(SERVICE_ZONE).toInstant();
        Instant endExclusive = run.getPeriodEnd().plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant();
        long myJourneyCount = journeys.countMyContribution(
                userId, pickupPointId, destinationDongId, startInclusive, endExclusive);

        List<DemandEstimate> cells = demandEstimates
                .findByRunIdAndPickupPointIdAndDestinationDongId(run.getId(), pickupPointId, destinationDongId);

        Visibility visibility = resolveVisibility(cells);
        // 표본 미달·셀 없음이면 후보 순위·역산값을 노출하지 않는다(candidateId/remainingToCandidate=NULL).
        String candidateId = visibility == Visibility.VISIBLE ? topCandidateId(cells) : null;
        // remainingToCandidate 는 후보 산정 정책 미확정이라 지금은 비워 둔다.
        return new Contribution(String.valueOf(run.getId()), myJourneyCount, visibility, candidateId, null);
    }

    private Visibility resolveVisibility(List<DemandEstimate> cells) {
        if (cells.isEmpty()) {
            return Visibility.NOT_AVAILABLE;
        }
        boolean anyVisible = cells.stream().anyMatch(c -> !c.isSuppressed());
        return anyVisible ? Visibility.VISIBLE : Visibility.INSUFFICIENT_SAMPLE;
    }

    /** 이 거점→목적지의 셀 중 가장 높은 후보(최소 rank)의 id. 후보가 없으면 null. */
    private String topCandidateId(List<DemandEstimate> cells) {
        return cells.stream()
                .filter(c -> c.getCandidateRank() != null)
                .min(Comparator.comparingInt(DemandEstimate::getCandidateRank))
                .map(c -> String.valueOf(c.getId()))
                .orElse(null);
    }

    private void requireStudent(Long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (user.getRole() != Role.STUDENT) {
            throw new OwllyException(ErrorCode.FORBIDDEN);
        }
    }
}
