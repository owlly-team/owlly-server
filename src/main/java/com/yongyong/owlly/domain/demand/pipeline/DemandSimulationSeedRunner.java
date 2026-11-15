package com.yongyong.owlly.domain.demand.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 데모 수요 데이터를 1회 시드한다(멱등). 기본은 꺼짐.
 *
 * <p>배포에서 {@code owlly.demand.seed-on-start=true}(env {@code OWLLY_DEMAND_SEED_ON_START=true}) 로 켜면
 * 한 번 심고, 이후 재기동에선 이미 있으므로 건너뛴다. 시드 실패가 앱 기동을 막지 않도록 예외를 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "owlly.demand.seed-on-start", havingValue = "true", matchIfMissing = false)
public class DemandSimulationSeedRunner implements ApplicationRunner {

    private final DemandSimulationSeeder seeder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seeder.seedAndGenerateOnce();
        } catch (RuntimeException e) {
            log.error("데모 수요 데이터 시드 실패(앱 기동은 계속)", e);
        }
    }
}
