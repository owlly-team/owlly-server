package com.yongyong.owlly.domain.auth.repository;

import com.yongyong.owlly.domain.auth.entity.StudentLoginReceipt;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentLoginReceiptRepository extends JpaRepository<StudentLoginReceipt, String> {
    long deleteByExpiresAtBefore(Instant now);
}
