package com.yongyong.owlly.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record StudentRegistrationRequest(
        @NotBlank @Size(max = 512) String verificationToken,
        @NotNull @Valid Profile profile
) {
    public record Profile(
            @NotBlank @Size(min = 2, max = 20) String realName,
            @NotBlank @Size(max = 32) String genderCode,
            Boolean sameGenderDefault,
            @NotNull @Valid Point defaultDropoff
    ) {
        public Profile {
            realName = realName == null ? null : realName.strip();
            sameGenderDefault = sameGenderDefault == null ? true : sameGenderDefault;
        }
    }

    public record Point(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {}
}
