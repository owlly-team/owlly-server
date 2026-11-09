package com.yongyong.owlly.domain.university.dto;

import java.util.List;

public record UniversityResponse(Long id, String name, List<String> emailDomains) {}
