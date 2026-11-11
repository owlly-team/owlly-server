package com.yongyong.owlly.domain.user.service;

import com.yongyong.owlly.domain.user.repository.StudentProfileUpdateReceiptRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StudentProfileReceiptCleanup {
    private final StudentProfileUpdateReceiptRepository receipts;

    @Scheduled(fixedDelayString = "${owlly.profile.cleanup-interval-ms:600000}", initialDelayString = "${owlly.profile.cleanup-interval-ms:600000}")
    @Transactional
    public void deleteExpired() { receipts.deleteExpired(Instant.now()); }
}
