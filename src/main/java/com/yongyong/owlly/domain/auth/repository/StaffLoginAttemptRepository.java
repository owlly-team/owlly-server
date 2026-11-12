package com.yongyong.owlly.domain.auth.repository;

import com.yongyong.owlly.domain.auth.entity.StaffLoginAttempt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StaffLoginAttemptRepository extends JpaRepository<StaffLoginAttempt, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StaffLoginAttempt a where a.accountKey = :accountKey")
    Optional<StaffLoginAttempt> findByAccountKeyForUpdate(@Param("accountKey") String accountKey);

    // 로그인 중인 행은 기다리지 않고 건너뛰어 다음 정리에서 처리한다.
    @Modifying
    @Query(value = """
            delete from staff_login_attempts
            where account_key in (
                select a.account_key from staff_login_attempts a
                where a.last_seen_at < :cutoff and (a.blocked_until is null or a.blocked_until <= :now)
                for update skip locked
            )
            """, nativeQuery = true)
    int deleteInactive(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
