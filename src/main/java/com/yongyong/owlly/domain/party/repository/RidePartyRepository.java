package com.yongyong.owlly.domain.party.repository;

import com.yongyong.owlly.domain.party.entity.RideParty;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 동승 팟 저장·조회.
 */
public interface RidePartyRepository extends JpaRepository<RideParty, Long> {

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

    /** 차단한 두 학생(작은 id, 큰 id). */
    interface BlockedPair {
        Long getLowUserId();

        Long getHighUserId();
    }
}
