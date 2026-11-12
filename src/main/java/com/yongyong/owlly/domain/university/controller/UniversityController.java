package com.yongyong.owlly.domain.university.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.domain.university.dto.UniversityResponse;
import com.yongyong.owlly.domain.university.service.UniversityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/universities")
@RequiredArgsConstructor
public class UniversityController {
    private final UniversityService service;

    @GetMapping
    public ApiResponse<List<UniversityResponse>> getUniversities() {
        return ApiResponse.of(service.getActiveUniversities());
    }
}
