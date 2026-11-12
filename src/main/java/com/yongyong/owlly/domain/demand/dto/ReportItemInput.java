package com.yongyong.owlly.domain.demand.dto;

import com.yongyong.owlly.domain.demand.entity.ReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 리포트에 담을 후보 하나(선택한 노선 셀 + 조치). 생성 요청용. */
public record ReportItemInput(
        @NotNull @Pattern(regexp = "^[1-9][0-9]*$", message = "estimateId 는 양의 정수 문자열이어야 합니다.") String estimateId,
        @NotNull ReportAction action) {}
