package com.yongyong.owlly.domain.demand.repository;

import com.yongyong.owlly.domain.demand.entity.StakeholderReportItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 리포트에 담긴 후보 항목 조회(리포트 내 순서대로). */
public interface StakeholderReportItemRepository extends JpaRepository<StakeholderReportItem, Long> {

    /** 한 리포트의 항목(표시 순서). */
    List<StakeholderReportItem> findByReportIdOrderByDisplayOrderAsc(Long reportId);

    /** 여러 리포트의 항목을 한 번에(목록 조회용). */
    List<StakeholderReportItem> findByReportIdInOrderByReportIdAscDisplayOrderAsc(Collection<Long> reportIds);
}
