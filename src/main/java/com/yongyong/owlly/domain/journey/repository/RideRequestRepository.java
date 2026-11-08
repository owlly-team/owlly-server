package com.yongyong.owlly.domain.journey.repository;

import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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
}
