package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.domain.auth.repository.StaffLoginAttemptRepository;
import com.yongyong.owlly.domain.auth.repository.StaffLoginReceiptRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StaffLoginCleanup {
    private final StaffLoginAttemptRepository attempts;
    private final StaffLoginReceiptRepository receipts;

    @Scheduled(fixedDelayString = "${owlly.staff-login.cleanup-interval-ms:600000}", initialDelayString = "${owlly.staff-login.cleanup-interval-ms:600000}")
    @Transactional
    public void deleteExpired() {
        Instant now = Instant.now();
        receipts.deleteExpired(now);
        attempts.deleteInactive(now.minus(30, ChronoUnit.DAYS), now);
    }
}
