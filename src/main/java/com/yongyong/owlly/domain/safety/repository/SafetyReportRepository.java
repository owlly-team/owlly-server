package com.yongyong.owlly.domain.safety.repository;

import com.yongyong.owlly.domain.safety.entity.SafetyReport;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SafetyReportRepository extends JpaRepository<SafetyReport, Long> {
    boolean existsByRoomIdAndReporterMemberIdAndTargetMemberId(Long roomId, Long reporterMemberId, Long targetMemberId);

    /** 각 신고의 증거만 만료시킨다. 진행 중인 채팅·차단·중복 방지 정보는 건드리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SafetyReport r set r.evidenceEncrypted = null, r.messageId = null, r.updatedAt = :now
            where r.evidenceDeleteAfter <= :now and r.evidenceEncrypted is not null
            """)
    int deleteExpiredEvidence(@Param("now") Instant now);
}
