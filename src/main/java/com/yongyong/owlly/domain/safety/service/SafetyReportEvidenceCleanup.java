package com.yongyong.owlly.domain.safety.service;

import com.yongyong.owlly.domain.safety.repository.SafetyReportRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "owlly.safety.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SafetyReportEvidenceCleanup {
    private final SafetyReportRepository reports;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void deleteExpired() {
        reports.deleteExpiredEvidence(Instant.now());
    }
}
