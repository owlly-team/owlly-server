package com.yongyong.owlly.domain.verification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일 단위 인증 실패 제한. 재발급하더라도 제한은 유지된다. */
@Entity
@Table(name = "verification_attempt_limits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationAttemptLimit {
    @Id @Column(length = 255)
    private String email;
    @Column(nullable = false)
    private int failures;
    private Instant blockedUntil;

    public VerificationAttemptLimit(String email) { this.email = email; }
    public void resetExpired(Instant now) {
        if (blockedUntil != null && !now.isBefore(blockedUntil)) reset();
    }
    public void fail(Instant now) {
        failures++;
        if (failures >= 5) blockedUntil = now.plusSeconds(600);
    }
    public void reset() { failures = 0; blockedUntil = null; }
}
