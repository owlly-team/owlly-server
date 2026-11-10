package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.auth.dto.ProfileOptionsResponse;
import com.yongyong.owlly.domain.user.entity.Gender;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfileOptionsService {
    public ProfileOptionsResponse getOptions() {
        return new ProfileOptionsResponse(List.of(
                new ProfileOptionsResponse.Option("FEMALE", "여성"),
                new ProfileOptionsResponse.Option("MALE", "남성")), true);
    }

    public Gender parseGender(String code) {
        return switch (code) {
            case "FEMALE" -> Gender.FEMALE;
            case "MALE" -> Gender.MALE;
            default -> throw new OwllyException(ErrorCode.INVALID_GENDER_CODE);
        };
    }
}
