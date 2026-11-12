package com.yongyong.owlly.domain.auth.entity;

import com.yongyong.owlly.common.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비밀번호·이메일 원문, 비밀번호 해시, 세션/CSRF 토큰은 저장하지 않는다. */
@Entity
@Getter
@Table(name = "staff_login_receipts", indexes = @Index(name = "idx_staff_receipt_expiry", columnList = "expires_at"))
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffLoginReceipt {
    @Id @Column(length = 36)
    private String requestKey;
    @Column(nullable = false, length = 64)
    private String requestHash;
    private Long userId;
    private Long universityId;
    @Enumerated(EnumType.STRING) @Column(length = 40)
    private ErrorCode errorCode;
    @Column(nullable = false)
    private Instant expiresAt;
}
