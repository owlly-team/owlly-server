package com.yongyong.owlly.domain.verification.dto;

import java.time.Instant;

public record EmailVerificationResponse(Long verificationId, Instant expiresAt, Instant resendAvailableAt) {}
