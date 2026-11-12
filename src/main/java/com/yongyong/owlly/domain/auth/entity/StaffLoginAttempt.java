package com.yongyong.owlly.domain.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정규화 이메일의 HMAC으로 관리하는 연속 실패/잠금. 계정의 SUSPENDED와 별개다. */
@Entity
@Getter
@Table(name = "staff_login_attempts", indexes = @Index(name = "idx_staff_attempt_last_seen", columnList = "last_seen_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffLoginAttempt {
    @Id @Column(length = 64)
    private String accountKey;
    @Column(nullable = false)
    private int failures;
    private Instant blockedUntil;
    @Column(nullable = false)
    private Instant lastSeenAt;

    public StaffLoginAttempt(String accountKey, Instant now) {
        this.accountKey = accountKey;
        this.lastSeenAt = now;
    }

    public void visit(Instant now) {
        lastSeenAt = now;
        if (blockedUntil != null && !now.isBefore(blockedUntil)) reset();
    }

    public void fail(Instant now, int maxFailures, long lockSeconds) {
        if (++failures >= maxFailures) blockedUntil = now.plusSeconds(lockSeconds);
    }

    public void reset() { failures = 0; blockedUntil = null; }
}
