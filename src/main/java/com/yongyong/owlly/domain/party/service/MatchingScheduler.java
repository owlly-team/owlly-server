package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.domain.party.dto.MatchingGroupKey;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭 작업을 1분마다 실행한다.
 *
 * <p>결과 안내 시각이 된 그룹을 찾아 그룹마다 매칭한다. 한 그룹이 실패해도 다음 그룹은 계속한다.
 * 로컬에서 끄려면 owlly.matching.enabled=false.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "owlly.matching.enabled", havingValue = "true", matchIfMissing = true)
public class MatchingScheduler {

    private final MatchingService matchingService;

    @Scheduled(fixedDelayString = "${owlly.matching.interval-ms:60000}")
    public void run() {
        Instant now = Instant.now();
        for (MatchingGroupKey key : matchingService.findDueGroups(now)) {
            try {
                int parties = matchingService.matchGroup(key, now);
                log.info("매칭 완료 key={} parties={}", key, parties);
            } catch (RuntimeException e) {
                log.error("매칭 실패 key={}", key, e);
            }
        }
    }
}
