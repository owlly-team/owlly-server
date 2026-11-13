package com.yongyong.owlly.domain.demand.dto;

import com.yongyong.owlly.domain.demand.entity.ReportFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 리포트 초안 생성 요청(API-46).
 *
 * <p>어느 산출(runId)의 어떤 조건(filters)으로, 어떤 후보들(items)을 담을지만 받는다.
 * 수요·순위·이미지/파일 경로 같은 수치는 요청에서 받지 않고 서버가 산출 결과에서 가져와 스냅샷으로 박제한다.
 */
public record StakeholderReportCreate(
        @NotNull @Pattern(regexp = "^[1-9][0-9]*$", message = "runId 는 양의 정수 문자열이어야 합니다.") String runId,
        @NotNull ReportFormat format,
        @NotNull @Valid ReportFilters filters,
        @NotEmpty @Valid List<ReportItemInput> items,
        @Size(max = 2000) String narrative) {}
