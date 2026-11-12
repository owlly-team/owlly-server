package com.yongyong.owlly.domain.journey.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 출발 시간대(슬롯) 규칙: 22:00~02:00 30분 단위, 출발 15분 전까지 등록, 결과 안내 출발 20분 전. */
class DepartureSlotPolicyTest {

    private final DepartureSlotPolicy policy = new DepartureSlotPolicy("22:00", "02:00");

    private static Instant kst(String dateTime) {
        return ZonedDateTime.parse(dateTime + "+09:00[Asia/Seoul]").toInstant();
    }

    @Test
    void 그날의_시간대는_새벽_5개와_밤_4개이고_자정을_넘기는_시간대는_실제_날짜로_센다() {
        List<Instant> slots = policy.slotsOn(LocalDate.of(2026, 11, 6));

        assertThat(slots).containsExactly(
                kst("2026-11-06T00:00:00"), kst("2026-11-06T00:30:00"), kst("2026-11-06T01:00:00"),
                kst("2026-11-06T01:30:00"), kst("2026-11-06T02:00:00"),
                kst("2026-11-06T22:00:00"), kst("2026-11-06T22:30:00"),
                kst("2026-11-06T23:00:00"), kst("2026-11-06T23:30:00"));
    }

    @Test
    void 서비스_시간_안의_30분_단위_정각만_허용한다() {
        assertThat(policy.isAllowedSlot(kst("2026-11-05T22:00:00"))).isTrue();
        assertThat(policy.isAllowedSlot(kst("2026-11-05T23:30:00"))).isTrue();
        assertThat(policy.isAllowedSlot(kst("2026-11-06T02:00:00"))).isTrue();

        assertThat(policy.isAllowedSlot(kst("2026-11-05T21:30:00"))).as("서비스 시작 전").isFalse();
        assertThat(policy.isAllowedSlot(kst("2026-11-06T02:30:00"))).as("02:00 이후는 없음").isFalse();
        assertThat(policy.isAllowedSlot(kst("2026-11-05T23:15:00"))).as("30분 단위가 아님").isFalse();
        assertThat(policy.isAllowedSlot(kst("2026-11-05T23:30:01"))).as("정각이 아님").isFalse();
        assertThat(policy.isAllowedSlot(kst("2026-11-05T14:00:00"))).as("낮 시간").isFalse();
    }

    @Test
    void 등록은_출발_15분_전까지() {
        Instant departure = kst("2026-11-05T23:30:00");

        assertThat(policy.isRegistrationOpen(departure, kst("2026-11-05T23:15:00"))).isTrue();
        assertThat(policy.isRegistrationOpen(departure, kst("2026-11-05T23:15:01"))).isFalse();
        assertThat(policy.isRegistrationOpen(departure, kst("2026-11-05T22:00:00"))).isTrue();
    }

    @Test
    void 결과_안내는_출발_20분_전() {
        assertThat(policy.resultDueAt(kst("2026-11-05T23:30:00"))).isEqualTo(kst("2026-11-05T23:10:00"));
    }

    @Test
    void 서비스_시간을_바꾸면_그_시간대를_쓴다() {
        DepartureSlotPolicy daytime = new DepartureSlotPolicy("13:00", "14:00");

        assertThat(daytime.slotsOn(LocalDate.of(2026, 11, 6)))
                .containsExactly(kst("2026-11-06T13:00:00"), kst("2026-11-06T13:30:00"), kst("2026-11-06T14:00:00"));
        assertThat(daytime.isAllowedSlot(kst("2026-11-06T13:30:00"))).isTrue();
        assertThat(daytime.isAllowedSlot(kst("2026-11-06T22:00:00"))).isFalse();
    }
}
