package com.yongyong.owlly.domain.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가입 재시도용 인프라 기록. 토큰·이름·좌표 원문이나 세션·CSRF 토큰을 저장하지 않는다. */
@Entity
@Getter
@Table(name = "student_registration_receipts")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentRegistrationReceipt {
    @Id
    @Column(length = 36)
    private String requestKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Long userId;

    /** 원래 인증 증표가 만료된 뒤에는 가입 재시도로 새 세션을 만들 수 없다. */
    @Column(nullable = false)
    private Instant expiresAt;
}
