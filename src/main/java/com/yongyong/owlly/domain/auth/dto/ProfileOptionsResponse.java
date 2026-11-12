package com.yongyong.owlly.domain.auth.dto;

import java.util.List;

public record ProfileOptionsResponse(List<Option> genderOptions, boolean sameGenderDefault) {
    public record Option(String code, String label) {}
}
