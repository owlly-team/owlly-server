package com.yongyong.owlly.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegistrationRequestLock {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "student-registration:" + key);
    }
}
