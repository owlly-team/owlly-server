package com.yongyong.owlly.domain.safety.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 두 학생 간 재매칭 금지. 대칭 차단.
 * 제약: CHECK(user_low_id < user_high_id), UNIQUE(user_low_id, user_high_id).
 */
@Entity
@Getter
@Table(name = "user_blocks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_block_pair", columnNames = {"user_low_id", "user_high_id"})
})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작은 사용자 ID FK -> users.id */
    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    /** 큰 사용자 ID FK -> users.id */
    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    /** 신고 원인 FK -> safety_reports.id. 신고 삭제 시 SET NULL. */
    @Column(name = "source_report_id")
    private Long sourceReportId;
}
