package com.yongyong.owlly.domain.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로필 변경 재시도 기록. 실명·좌표·세션·응답 원문을 보관하지 않는다. */
@Entity
@Getter
@Table(name = "student_profile_update_receipts", indexes = @Index(name = "idx_profile_receipt_expiry", columnList = "expires_at"))
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfileUpdateReceipt {
    @Id @Column(length = 36)
    private String requestKey;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false)
    private Instant expiresAt;
}
