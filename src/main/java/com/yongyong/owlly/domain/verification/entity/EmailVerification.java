package com.yongyong.owlly.domain.verification.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가입 전 인증번호 발급·소비 이력. 성공 처리는 한 번만 허용. */
@Entity
@Getter
@Table(name = "email_verifications")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> universities.id */
    @Column(name = "university_id", nullable = false)
    private Long universityId;

    /** 정규화 이메일. */
    @Column(nullable = false, length = 255)
    private String email;

    /** 6자리 코드의 비밀키 기반 해시. 원문 저장 금지. */
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /** 발급 후 10분. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** 재발급 등으로 폐기한 시각. */
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;
}
