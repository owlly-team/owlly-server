package com.yongyong.owlly.domain.auth.repository;

import com.yongyong.owlly.domain.auth.entity.StaffLoginReceipt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StaffLoginReceiptRepository extends JpaRepository<StaffLoginReceipt, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StaffLoginReceipt r where r.requestKey = :requestKey")
    Optional<StaffLoginReceipt> findByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    // 만료 기록도 로그인 재시도가 갱신할 수 있으므로 잠긴 행을 삭제하지 않는다.
    @Modifying
    @Query(value = """
            delete from staff_login_receipts
            where request_key in (
                select r.request_key from staff_login_receipts r
                where r.expires_at < :now
                for update skip locked
            )
            """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
