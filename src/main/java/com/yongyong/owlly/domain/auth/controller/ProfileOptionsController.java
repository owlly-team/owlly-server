package com.yongyong.owlly.domain.auth.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.domain.auth.dto.ProfileOptionsResponse;
import com.yongyong.owlly.domain.auth.service.ProfileOptionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProfileOptionsController {
    private final ProfileOptionsService options;

    @GetMapping("/api/v1/profile-options")
    public ApiResponse<ProfileOptionsResponse> getOptions() {
        return ApiResponse.of(options.getOptions());
    }
}
