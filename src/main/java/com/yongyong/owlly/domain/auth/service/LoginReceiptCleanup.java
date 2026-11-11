package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.domain.auth.repository.StudentLoginReceiptRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LoginReceiptCleanup {
    private final StudentLoginReceiptRepository receipts;

    @Scheduled(fixedDelayString = "${owlly.login.cleanup-interval-ms:600000}", initialDelayString = "${owlly.login.cleanup-interval-ms:600000}")
    @Transactional
    public void deleteExpired() {
        receipts.deleteByExpiresAtBefore(Instant.now());
    }
}
