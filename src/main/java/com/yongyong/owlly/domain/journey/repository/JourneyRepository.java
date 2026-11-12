package com.yongyong.owlly.domain.journey.repository;

import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 귀가 조회.
 *
 * <p>내 귀가 기록 조회(API-16): 홈에서 진행 중인 동승이 있는지 확인하거나(동승 카드),
 * 내 정보에서 지난 귀가를 볼 때 쓰인다. 최근 귀가부터 보여 준다
 * (ERD 제안 인덱스 journeys(user_id, intended_departure_at DESC)).
 */
public interface JourneyRepository extends JpaRepository<Journey, Long> {

    /** 내 귀가 첫 페이지. 귀가 예정 시각 최신순, 같으면 id 큰 순. */
    @Query("""
            select j from Journey j
            where j.userId = :userId
            order by j.intendedDepartureAt desc, j.id desc
            """)
    List<Journey> findMyFirstPage(@Param("userId") Long userId, Pageable pageable);

    /**
     * 내 귀가 다음 페이지. 직전 페이지의 마지막 귀가(cursorAt, cursorId) 다음부터.
     * 예) 스크롤을 내려 "더 이전 귀가"를 불러올 때.
     */
    @Query("""
            select j from Journey j
            where j.userId = :userId
              and (j.intendedDepartureAt < :cursorAt
                   or (j.intendedDepartureAt = :cursorAt and j.id < :cursorId))
            order by j.intendedDepartureAt desc, j.id desc
            """)
    List<Journey> findMyNextPage(@Param("userId") Long userId,
                                 @Param("cursorAt") Instant cursorAt,
                                 @Param("cursorId") Long cursorId,
                                 Pageable pageable);

    /**
     * 결과를 기록하기 전에 귀가를 잠가서 가져온다(트랜잭션이 끝날 때까지 같은 귀가의 다른 변경은 기다린다).
     * 예) 막차 선택 버튼을 거의 동시에 두 번 눌러도 한 번만 기록되고, 두 번째는 같은 결과를 본다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Journey j where j.id = :id")
    Optional<Journey> findByIdForUpdate(@Param("id") Long id);

    /**
     * 귀가 생성 재시도 확인용. 같은 멱등키로 이미 만든 귀가가 있으면 그것을 돌려준다.
     * 예) 응답을 못 받아 같은 요청을 다시 보냈을 때 귀가가 두 개 생기지 않게 한다.
     */
    Optional<Journey> findByClientJourneyKey(UUID clientJourneyKey);

    /**
     * 한 학생의 귀가 생성을 한 번에 하나씩만 처리하려고 학생 줄을 잠근다.
     * 예) 같은 학생이 두 기기에서 동시에 귀가를 만들어도 "진행 중인 동승" 확인과 저장이 겹치지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> lockUser(@Param("userId") Long userId);

    /**
     * 산출 기간 동안 내가 특정 거점→목적지로 남긴 귀가 수(수요 반영 횟수, API-45).
     * 기간은 [startInclusive, endExclusive) 로 받는다.
     */
    @Query("""
            select count(j) from Journey j
            where j.userId = :userId
              and j.pickupPointId = :pickupPointId
              and j.destinationDongId = :destinationDongId
              and j.intendedDepartureAt >= :startInclusive
              and j.intendedDepartureAt < :endExclusive
            """)
    long countMyContribution(@Param("userId") Long userId,
                             @Param("pickupPointId") Long pickupPointId,
                             @Param("destinationDongId") Long destinationDongId,
                             @Param("startInclusive") Instant startInclusive,
                             @Param("endExclusive") Instant endExclusive);
}
