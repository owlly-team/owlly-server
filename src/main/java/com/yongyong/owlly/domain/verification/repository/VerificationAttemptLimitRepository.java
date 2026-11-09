package com.yongyong.owlly.domain.verification.repository;

import com.yongyong.owlly.domain.verification.entity.VerificationAttemptLimit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationAttemptLimitRepository extends JpaRepository<VerificationAttemptLimit, String> {}
