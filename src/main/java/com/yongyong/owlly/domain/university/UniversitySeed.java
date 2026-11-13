package com.yongyong.owlly.domain.university;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 없는 대학과 도메인만 생성하며, 기존 항목의 활성 상태는 덮어쓰지 않는다. */
@Order(10)
@Component
@RequiredArgsConstructor
public class UniversitySeed implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : SupportedUniversities.NAMES) {
            jdbc.update("insert into universities (name, is_active, created_at, updated_at) "
                    + "values (?, true, current_timestamp, current_timestamp) on conflict (name) do nothing", name);
            String domain = SupportedUniversities.EMAIL_DOMAINS.get(name);
            jdbc.update("insert into university_domains (university_id, domain, is_active, created_at, updated_at) "
                    + "select id, ?, true, current_timestamp, current_timestamp from universities where name = ? "
                    + "on conflict (domain) do nothing", domain, name);
        }
    }
}
