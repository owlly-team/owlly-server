package com.yongyong.owlly.domain.party.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 확정 마감 처리를 1분마다 실행한다.
 *
 * <p>확정 마감이 지난 팟을 찾아 팟마다 처리한다(확정하지 않은 멤버 제외, 대표 미확정·인원 부족이면 해산).
 * 한 팟이 실패해도 다음 팟은 계속한다. 매칭 작업과 같은 설정으로 끈다: owlly.matching.enabled=false.
 * (스케줄링 켜기는 MatchingScheduler 의 @EnableScheduling 이 한다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "owlly.matching.enabled", havingValue = "true", matchIfMissing = true)
public class PartyConfirmationScheduler {

    private final PartyService partyService;

    @Scheduled(fixedDelayString = "${owlly.matching.interval-ms:60000}")
    public void run() {
        Instant now = Instant.now();
        for (Long partyId : partyService.findPartyIdsPastConfirmDeadline(now)) {
            try {
                if (partyService.closeConfirmation(partyId, now)) {
                    log.info("확정 마감 처리 완료 partyId={}", partyId);
                }
            } catch (RuntimeException e) {
                log.error("확정 마감 처리 실패 partyId={}", partyId, e);
            }
        }
    }
}
