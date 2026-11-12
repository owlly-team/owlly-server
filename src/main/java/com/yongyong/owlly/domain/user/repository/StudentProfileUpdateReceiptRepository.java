package com.yongyong.owlly.domain.user.repository;

import com.yongyong.owlly.domain.user.entity.StudentProfileUpdateReceipt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StudentProfileUpdateReceiptRepository extends JpaRepository<StudentProfileUpdateReceipt, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StudentProfileUpdateReceipt r where r.requestKey = :key")
    Optional<StudentProfileUpdateReceipt> findByKeyForUpdate(@Param("key") String key);

    @Modifying
    @Query(value = """
            delete from student_profile_update_receipts
            where request_key in (
                select r.request_key from student_profile_update_receipts r
                where r.expires_at < :now
                for update skip locked
            )
            """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
