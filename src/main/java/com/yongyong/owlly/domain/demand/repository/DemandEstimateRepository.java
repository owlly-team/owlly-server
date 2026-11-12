package com.yongyong.owlly.domain.demand.repository;

import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 노선 후보(수요 셀) 조회.
 *
 * <p>목록은 후보 순위(candidate_rank) 오름차순으로 보여 주고, 후보가 아닌 셀(rank NULL)은 뒤에 둔다.
 * 최소 표본 미달(is_suppressed)은 목록에서 제외한다. 커서는 (유효순위, id) keyset 으로 이어 받는다.
 */
public interface DemandEstimateRepository extends JpaRepository<DemandEstimate, Long> {

    /**
     * 한 산출(runId) 안에서 필터·커서로 노선 후보 한 페이지를 읽는다(limit 개).
     * candidateOnly 면 후보만, curRank/curId 가 있으면 그 지점 다음부터. 호출부에서 limit+1 로 다음 페이지 유무를 본다.
     * 유효순위 = COALESCE(candidate_rank, 2147483647) 로 비후보를 맨 뒤로 보낸다.
     */
    @Query(value = """
            SELECT * FROM demand_estimates e
            WHERE e.run_id = :runId
              AND e.is_suppressed = false
              AND (:weekday IS NULL OR e.weekday = :weekday)
              AND (CAST(:slotStart AS time) IS NULL OR e.slot_start = CAST(:slotStart AS time))
              AND (:candidateOnly = false OR e.candidate_rank IS NOT NULL)
              AND (:curRank IS NULL
                   OR COALESCE(e.candidate_rank, 2147483647) > :curRank
                   OR (COALESCE(e.candidate_rank, 2147483647) = :curRank AND e.id > :curId))
            ORDER BY COALESCE(e.candidate_rank, 2147483647) ASC, e.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DemandEstimate> findPage(
            @Param("runId") Long runId,
            @Param("weekday") Integer weekday,
            @Param("slotStart") LocalTime slotStart,
            @Param("candidateOnly") boolean candidateOnly,
            @Param("curRank") Integer curRank,
            @Param("curId") Long curId,
            @Param("limit") int limit);

    /** 한 산출에서 특정 거점→목적지의 모든 수요 셀(요일·슬롯별). API-45 기여 판정에 쓴다. */
    List<DemandEstimate> findByRunIdAndPickupPointIdAndDestinationDongId(
            Long runId, Long pickupPointId, Long destinationDongId);

    /**
     * 리포트 상단 집계(API-46 생성 시 박제). 리포트 필터와 같은 조건으로 표시 가능한 셀 전체를 합산한다.
     * 반환: [journeyCount, failedJourneyCount, soloSelectedCount, busSelectedCount] 한 행.
     */
    @Query(value = """
            SELECT coalesce(sum(e.journey_count), 0),
                   coalesce(sum(e.failed_journey_count), 0),
                   coalesce(sum(e.solo_selected_count), 0),
                   coalesce(sum(e.bus_selected_count), 0)
            FROM demand_estimates e
            WHERE e.run_id = :runId
              AND e.is_suppressed = false
              AND (:weekday IS NULL OR e.weekday = :weekday)
              AND (CAST(:slotStart AS time) IS NULL OR e.slot_start = CAST(:slotStart AS time))
            """, nativeQuery = true)
    List<Object[]> summarize(
            @Param("runId") Long runId,
            @Param("weekday") Integer weekday,
            @Param("slotStart") LocalTime slotStart);
}
