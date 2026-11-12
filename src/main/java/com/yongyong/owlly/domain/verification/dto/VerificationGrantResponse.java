package com.yongyong.owlly.domain.verification.dto;

import java.time.Instant;

public record VerificationGrantResponse(String verificationToken, Instant expiresAt) {}
