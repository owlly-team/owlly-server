package com.yongyong.owlly.domain.demand.pipeline;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수요 산출 배치: 매일 새벽 1시(Asia/Seoul)에 지난 7일을 대상으로 산출을 돌린다.
 *
 * <p>기본은 꺼짐(owlly.demand.enabled=true 로 켬) — 산출 로직·데이터가 준비된 환경에서만 돌린다.
 * cron 은 owlly.demand.cron 으로 바꿀 수 있다. 한 번 실패해도 다음 날 실행에 영향을 주지 않는다.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "owlly.demand.enabled", havingValue = "true", matchIfMissing = false)
public class DemandRunScheduler {

    private final DemandRunGenerator generator;

    @Scheduled(cron = "${owlly.demand.cron:0 0 1 * * *}", zone = "Asia/Seoul")
    public void run() {
        LocalDate end = LocalDate.now(DemandAggregator.SERVICE_ZONE).minusDays(1);
        LocalDate start = end.minusDays(6);
        try {
            var runResult = generator.generate(start, end);
            log.info("수요 산출 완료 runId={} 기간={}~{}", runResult.getId(), start, end);
        } catch (RuntimeException e) {
            log.error("수요 산출 실패 기간={}~{}", start, end, e);
        }
    }
}
