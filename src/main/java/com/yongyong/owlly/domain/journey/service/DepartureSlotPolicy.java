package com.yongyong.owlly.domain.journey.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 동승 출발 시간대(슬롯) 규칙 (화면정의서 공통 정책 "서비스 시간", S-03 출발 시간 선택).
 *
 * <ul>
 *   <li>출발 시간대는 22:00~02:00, 30분 단위(02:00 이후는 없음). 한국 시간 기준.</li>
 *   <li>출발 15분 전이 지난 시간대는 등록할 수 없다(등록·취소 마감이 출발 15분 전).</li>
 *   <li>결과 안내는 막차 정보가 없을 때 출발 20분 전(막차 후 등록 기준).</li>
 * </ul>
 * 시연·테스트에서 다른 시간대가 필요하면 owlly.matching.slot-start / slot-end 로 바꾼다(기본 22:00 / 02:00).
 */
@Component
public class DepartureSlotPolicy {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int STEP_MINUTES = 30;
    static final Duration REGISTRATION_CLOSES_BEFORE_DEPARTURE = Duration.ofMinutes(15);
    static final Duration RESULT_DUE_BEFORE_DEPARTURE = Duration.ofMinutes(20);

    private final LocalTime serviceStart;
    private final LocalTime serviceEnd;

    public DepartureSlotPolicy(@Value("${owlly.matching.slot-start:22:00}") String slotStart,
                               @Value("${owlly.matching.slot-end:02:00}") String slotEnd) {
        this.serviceStart = LocalTime.parse(slotStart);
        this.serviceEnd = LocalTime.parse(slotEnd);
    }

    /**
     * 그날(한국 날짜)에 있는 출발 시간대들. 자정을 넘기는 시간대는 실제 날짜로 센다.
     * 예) 11.6 이면 00:00 00:30 01:00 01:30 02:00 22:00 22:30 23:00 23:30 (11.6 새벽 5개 + 밤 4개).
     */
    public List<Instant> slotsOn(LocalDate date) {
        List<Instant> slots = new ArrayList<>();
        for (LocalTime time = LocalTime.MIDNIGHT; ; time = time.plusMinutes(STEP_MINUTES)) {
            if (isInServiceWindow(time)) {
                slots.add(ZonedDateTime.of(date, time, KST).toInstant());
            }
            if (time.equals(LocalTime.of(23, 30))) {
                break;
            }
        }
        return slots;
    }

    /** 서버가 허용하는 시간대인지: 30분 단위 정각이고 서비스 시간 안. */
    public boolean isAllowedSlot(Instant departureAt) {
        ZonedDateTime local = departureAt.atZone(KST);
        return local.getSecond() == 0 && local.getNano() == 0
                && local.getMinute() % STEP_MINUTES == 0
                && isInServiceWindow(local.toLocalTime());
    }

    /** 지금 이 시간대에 등록할 수 있는지: 출발 15분 전까지. */
    public boolean isRegistrationOpen(Instant departureAt, Instant now) {
        return !now.isAfter(departureAt.minus(REGISTRATION_CLOSES_BEFORE_DEPARTURE));
    }

    /** 결과 안내 시각: 출발 20분 전(막차 정보 연동 전에는 막차 후 등록 기준으로 계산). */
    public Instant resultDueAt(Instant departureAt) {
        return departureAt.minus(RESULT_DUE_BEFORE_DEPARTURE);
    }

    /** 22:00~02:00 처럼 자정을 넘기는 구간도 처리한다. */
    private boolean isInServiceWindow(LocalTime time) {
        if (serviceStart.isAfter(serviceEnd)) {
            return !time.isBefore(serviceStart) || !time.isAfter(serviceEnd);
        }
        return !time.isBefore(serviceStart) && !time.isAfter(serviceEnd);
    }
}
