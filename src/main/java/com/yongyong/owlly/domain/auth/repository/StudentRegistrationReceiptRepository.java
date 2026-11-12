package com.yongyong.owlly.domain.auth.repository;

import com.yongyong.owlly.domain.auth.entity.StudentRegistrationReceipt;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRegistrationReceiptRepository extends JpaRepository<StudentRegistrationReceipt, String> {
    long deleteByExpiresAtBefore(Instant now);
}
