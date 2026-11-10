package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.domain.auth.repository.StudentRegistrationReceiptRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 인증 증표가 만료된 가입 재시도 기록을 정리한다. 업무 계정·프로필에는 영향이 없다. */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RegistrationReceiptCleanup {
    private final StudentRegistrationReceiptRepository receipts;

    @Scheduled(fixedDelayString = "${owlly.registration.cleanup-interval-ms:600000}", initialDelayString = "${owlly.registration.cleanup-interval-ms:600000}")
    @Transactional
    public void deleteExpired() {
        receipts.deleteByExpiresAtBefore(Instant.now());
    }
}
