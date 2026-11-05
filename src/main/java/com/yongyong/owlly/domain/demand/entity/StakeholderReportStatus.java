package com.yongyong.owlly.domain.demand.entity;

/** 리포트 생성 상태. FAILED 재시도는 같은 리포트로 가능. */
public enum StakeholderReportStatus {
    DRAFT,
    GENERATING,
    READY,
    FAILED
}
