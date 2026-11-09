package com.yongyong.owlly.domain.notification.repository;

import com.yongyong.owlly.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 알림 저장·조회.
 *
 * <p>매칭 결과·정산 요청 같은 사건이 생기면 알림을 남긴다. 남긴 알림은 푸시로 보내고(C-04), 앱에서 다시 조회할 수도 있다.
 * 같은 사건의 알림은 dedupe_key(중복 제거 키)로 한 번만 남긴다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 같은 dedupe_key 알림이 없을 때만 넣는다. 이미 있으면 아무것도 하지 않는다(오류 없음).
     * 예) 매칭 작업이 재시도돼서 "매칭됐어요" 알림을 두 번 만들려고 해도 한 번만 남는다.
     * 반환값: 실제로 넣은 행 수(1 = 새로 만듦, 0 = 이미 있었음).
     */
    @Modifying
    @Query(value = """
            insert into notifications (user_id, party_id, type, dedupe_key, payload, scheduled_at, created_at, updated_at)
            values (:userId, cast(:partyId as bigint), :type, :dedupeKey, cast(:payload as jsonb), :scheduledAt, now(), now())
            on conflict (dedupe_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("partyId") Long partyId,
                       @Param("type") String type,
                       @Param("dedupeKey") String dedupeKey,
                       @Param("payload") String payload,
                       @Param("scheduledAt") Instant scheduledAt);

    /** 내 알림 첫 페이지. 보낼 시각이 지난 것만, 최신순. */
    @Query("""
            select n from Notification n
            where n.userId = :userId and n.scheduledAt <= :now
            order by n.scheduledAt desc, n.id desc
            """)
    List<Notification> findMyFirstPage(@Param("userId") Long userId,
                                       @Param("now") Instant now,
                                       Pageable pageable);

    /** 내 알림 다음 페이지. 직전 페이지의 마지막 알림(cursorAt, cursorId) 다음부터. */
    @Query("""
            select n from Notification n
            where n.userId = :userId and n.scheduledAt <= :now
              and (n.scheduledAt < :cursorAt
                   or (n.scheduledAt = :cursorAt and n.id < :cursorId))
            order by n.scheduledAt desc, n.id desc
            """)
    List<Notification> findMyNextPage(@Param("userId") Long userId,
                                      @Param("now") Instant now,
                                      @Param("cursorAt") Instant cursorAt,
                                      @Param("cursorId") Long cursorId,
                                      Pageable pageable);

    /** 내 알림 한 건. 남의 알림이면 비어 있다. */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
