package com.yongyong.owlly.domain.verification.repository;

import com.yongyong.owlly.domain.verification.entity.VerificationFailedRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationFailedRequestRepository extends JpaRepository<VerificationFailedRequest, String> {}
