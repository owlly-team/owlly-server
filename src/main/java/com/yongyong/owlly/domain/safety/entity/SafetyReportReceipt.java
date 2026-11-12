package com.yongyong.owlly.domain.safety.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신고 재시도 식별 정보만 보존한다. 설명·대화 원문이나 증거 사본을 담지 않는다. */
@Entity
@Table(name = "safety_report_receipts")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyReportReceipt {
    @Id @Column(name = "request_key", length = 36) private String key;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "report_id", nullable = false) private Long reportId;
}
