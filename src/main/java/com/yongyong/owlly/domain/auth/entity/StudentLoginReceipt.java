package com.yongyong.owlly.domain.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 재시도 기록. 인증 증표·세션·CSRF 토큰 원문은 저장하지 않는다. */
@Entity
@Getter
@Table(name = "student_login_receipts")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentLoginReceipt {
    @Id
    @Column(length = 36)
    private String requestKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long universityId;

    @Column(nullable = false)
    private Instant expiresAt;
}
