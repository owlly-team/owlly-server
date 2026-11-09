package com.yongyong.owlly.domain.verification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CodeVerificationRequest(@NotNull @Pattern(regexp = "[0-9]{6}") String code) {}
