package com.yongyong.owlly.domain.auth.dto;

import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import java.time.Instant;

public record SessionResponse(CurrentUser user, Instant expiresAt, String csrfToken) {
    public record CurrentUser(Long id, Long universityId, String universityName, String email, Role role, UserStatus status) {
        public static CurrentUser from(User user, String universityName) {
            return new CurrentUser(user.getId(), user.getUniversityId(), universityName, user.getEmail(), user.getRole(), user.getStatus());
        }
    }
}
