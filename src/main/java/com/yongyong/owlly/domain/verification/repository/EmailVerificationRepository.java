package com.yongyong.owlly.domain.verification.repository;

import com.yongyong.owlly.domain.verification.entity.EmailVerification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByIssueKey(String key);
    Optional<EmailVerification> findByVerifyKey(String key);
    @org.springframework.data.jpa.repository.Query("select v.id from EmailVerification v where v.grantHash = :hash")
    Optional<Long> findIdByGrantHash(String hash);
    Optional<EmailVerification> findFirstByEmailOrderBySentAtDescIdDesc(String email);
    long countByEmailAndSentAtGreaterThanEqualAndSentAtLessThan(String email, Instant start, Instant end);
    List<EmailVerification> findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(String email);
    @org.springframework.data.jpa.repository.Query("select v.email from EmailVerification v where v.id = :id")
    Optional<String> findEmailById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from EmailVerification v where v.id = :id")
    Optional<EmailVerification> findForUpdate(Long id);
}
