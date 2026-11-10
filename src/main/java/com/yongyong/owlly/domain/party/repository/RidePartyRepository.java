package com.yongyong.owlly.domain.party.repository;

import com.yongyong.owlly.domain.party.entity.RideParty;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 동승 팟 저장·조회.
 */
public interface RidePartyRepository extends JpaRepository<RideParty, Long> {

    /**
     * 상태를 바꾸기 전에 팟을 잠가서 가져온다(트랜잭션이 끝날 때까지 다른 쪽은 기다린다).
     * 예) 마지막 두 사람이 동시에 확정해도 "전원 확정 → 채팅방 열기"가 한 번만, 빠짐없이 일어나게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RideParty p where p.id = :id")
    Optional<RideParty> findByIdForUpdate(@Param("id") Long id);

    /**
     * 주어진 학생들 사이의 차단 관계(user_blocks). 읽기만 한다.
     * 예) 매칭 작업이 서로 차단한 학생을 같은 팟에 넣지 않으려고 확인할 때. 차단은 신고(API-30)가 만든다.
     */
    @Query(value = """
            select b.user_low_id as lowUserId, b.user_high_id as highUserId
            from user_blocks b
            where b.user_low_id in (:userIds) and b.user_high_id in (:userIds)
            """, nativeQuery = true)
    List<BlockedPair> findBlockedPairs(@Param("userIds") Collection<Long> userIds);

    /**
     * 팟의 채팅방 번호(팟당 최대 1개). 읽기만 한다. 방은 채팅 쪽에서 연다.
     * 예) 매칭 결과 화면에서 확정한 학생에게 "채팅방으로 가기"를 보여 줄 때.
     */
    @Query("select c.id from ChatRoom c where c.partyId = :partyId")
    Optional<Long> findRoomIdByPartyId(@Param("partyId") Long partyId);

    /** 차단한 두 학생(작은 id, 큰 id). */
    interface BlockedPair {
        Long getLowUserId();

        Long getHighUserId();
    }
}
