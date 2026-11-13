package com.yongyong.owlly.domain.demand.repository;

import com.yongyong.owlly.domain.demand.entity.StakeholderReport;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관계자 리포트 조회.
 *
 * <p>목록은 본인이 만든 리포트만 최신순으로 보여 준다(id 내림차순, 커서 페이징).
 */
public interface StakeholderReportRepository extends JpaRepository<StakeholderReport, Long> {

    /** 내 리포트 첫 페이지(최신순). */
    List<StakeholderReport> findByCreatedByOrderByIdDesc(Long createdBy, Pageable pageable);

    /** 내 리포트 다음 페이지(직전 페이지 마지막 id 이전). */
    List<StakeholderReport> findByCreatedByAndIdLessThanOrderByIdDesc(
            Long createdBy, Long cursorId, Pageable pageable);
}
