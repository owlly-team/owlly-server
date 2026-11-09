package com.yongyong.owlly.domain.verification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 실패한 인증 요청의 재시도 결과. 인증번호 원문은 저장하지 않는다. */
@Entity
@Table(name = "verification_failed_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationFailedRequest {
    @Id @Column(name = "request_key", length = 36)
    private String requestKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "blocked_until")
    private Instant blockedUntil;

    public VerificationFailedRequest(String requestKey, String requestHash, Instant expiresAt, Instant blockedUntil) {
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
        this.blockedUntil = blockedUntil;
    }
}
