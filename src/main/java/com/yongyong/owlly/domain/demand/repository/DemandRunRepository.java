package com.yongyong.owlly.domain.demand.repository;

import com.yongyong.owlly.domain.demand.entity.DemandRun;
import com.yongyong.owlly.domain.demand.entity.DemandRunStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 수요 산출 실행 조회.
 *
 * <p>목록 조회에서 runId 를 지정하지 않으면 가장 최근 성공한 산출을 기본으로 쓴다.
 * 기간(periodStart·periodEnd)이 함께 오면 그 기간의 산출을 고른다.
 */
public interface DemandRunRepository extends JpaRepository<DemandRun, Long> {

    /** 가장 최근에 성공한 산출(기간 종료일·id 내림차순). */
    Optional<DemandRun> findFirstByStatusOrderByPeriodEndDescIdDesc(DemandRunStatus status);

    /** 특정 기간의 가장 최근 성공한 산출. */
    Optional<DemandRun> findFirstByStatusAndPeriodStartAndPeriodEndOrderByIdDesc(
            DemandRunStatus status, LocalDate periodStart, LocalDate periodEnd);
}
