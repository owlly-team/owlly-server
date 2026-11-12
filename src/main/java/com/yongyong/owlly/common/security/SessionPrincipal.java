package com.yongyong.owlly.common.security;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/** 세션에는 식별자만 저장한다. 역할·계정 상태는 매 요청 DB에서 확인한다. */
public record SessionPrincipal(Long userId) implements AuthenticatedPrincipal, Serializable {
    @Override
    public String getName() {
        return userId.toString();
    }
}
