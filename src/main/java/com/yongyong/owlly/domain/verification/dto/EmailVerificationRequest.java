package com.yongyong.owlly.domain.verification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(@NotNull @Positive Long universityId,
        @NotBlank @Email @Size(max = 255) String email) {}
