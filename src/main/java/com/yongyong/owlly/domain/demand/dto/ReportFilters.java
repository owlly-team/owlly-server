package com.yongyong.owlly.domain.demand.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 리포트가 집계 대상으로 삼은 조건(기간 필수, 요일·시간대 선택).
 *
 * <p>생성 요청에서 받고, 저장 시 filters_snapshot(jsonb)으로 박제해 조회 때 그대로 돌려준다.
 * slotStart 는 30분 슬롯 "HH:mm". weekday 는 0=일요일.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportFilters(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
        @Min(0) @Max(6) Integer weekday,
        @Pattern(regexp = "^(?:[01][0-9]|2[0-3]):(?:00|30)$", message = "slotStart 는 HH:mm(30분 단위)여야 합니다.")
        String slotStart) {}
