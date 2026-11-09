package com.yongyong.owlly.domain.journey.repository;

import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.party.dto.MatchingGroupKey;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * 동승 신청 조회.
 *
 * <p>한 귀가에는 신청이 여러 번 남을 수 있지만(실패 후 재등록), 동시에 진행 중인 신청은 최대 1개다.
 */
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    /**
     * 여러 귀가의 신청 중 지정한 상태인 것만. 귀가 목록마다 따로 조회하지 않고 한 번에 가져온다.
     * 예) 내 귀가 기록에서 각 귀가의 "진행 중인 신청"(WAITING·MATCHED)을 찾을 때.
     */
    List<RideRequest> findByJourneyIdInAndStatusIn(Collection<Long> journeyIds,
                                                   Collection<RideRequestStatus> statuses);

    /**
     * 상태를 바꾸기 전에 신청을 잠가서 가져온다(트랜잭션이 끝날 때까지 다른 쪽은 기다린다).
     * 예) 학생의 "등록 취소"와 매칭 작업이 같은 신청을 동시에 바꿔 이중으로 처리되는 것을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RideRequest r where r.id = :id")
    Optional<RideRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * 결과 안내 시각이 된 대기 신청들의 매칭 키(학교·거점·도착 구·출발 시각) 목록. 잠그지 않는다.
     * 결과 안내 시각(result_due_at)이 비어 있으면 출발 시각이 dueDepartureLimit 이하인 신청을 대상으로 본다.
     * 예) 매칭 작업이 1분마다 "지금 처리할 그룹"을 찾을 때.
     */
    @Query("""
            select distinct new com.yongyong.owlly.domain.party.dto.MatchingGroupKey(
                r.universityId, r.pickupPointId, r.districtId, r.departureAt)
            from RideRequest r
            where r.status = com.yongyong.owlly.domain.journey.entity.RideRequestStatus.WAITING
              and (r.resultDueAt <= :now or (r.resultDueAt is null and r.departureAt <= :dueDepartureLimit))
            """)
    List<MatchingGroupKey> findDueGroupKeys(@Param("now") Instant now,
                                            @Param("dueDepartureLimit") Instant dueDepartureLimit);

    /**
     * 한 그룹의 결과 안내 시각이 된 대기 신청을 먼저 신청한 순서(created_at, id)로 잠가서 가져온다.
     * 다른 트랜잭션이 잠근 신청(예: 지금 취소 중인 신청)은 기다리지 않고 건너뛴다(skip locked).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select r from RideRequest r
            where r.status = com.yongyong.owlly.domain.journey.entity.RideRequestStatus.WAITING
              and r.universityId = :universityId
              and r.pickupPointId = :pickupPointId
              and r.districtId = :districtId
              and r.departureAt = :departureAt
              and (r.resultDueAt <= :now or (r.resultDueAt is null and r.departureAt <= :dueDepartureLimit))
            order by r.createdAt asc, r.id asc
            """)
    List<RideRequest> findDueInGroupForUpdate(@Param("universityId") Long universityId,
                                              @Param("pickupPointId") Long pickupPointId,
                                              @Param("districtId") Long districtId,
                                              @Param("departureAt") Instant departureAt,
                                              @Param("now") Instant now,
                                              @Param("dueDepartureLimit") Instant dueDepartureLimit);
}
