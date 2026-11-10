package com.yongyong.owlly.domain.journey.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 번의 귀가 의도와 최종 선택.
 * 재등록은 같은 journey 를 재사용하고 ride_requests 를 새로 생성한다.
 */
@Entity
@Getter
@Table(name = "journeys")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Journey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 귀가 생성 재시도의 멱등키. */
    @Column(name = "client_journey_key", nullable = false, unique = true)
    private UUID clientJourneyKey;

    /** FK -> users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 생성 시 대학 스냅샷 FK -> universities.id */
    @Column(name = "university_id", nullable = false)
    private Long universityId;

    /** 최종 출발 거점 FK -> pickup_points.id */
    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    /** 최종 하차 동 FK -> administrative_areas.id */
    @Column(name = "destination_dong_id", nullable = false)
    private Long destinationDongId;

    @Column(name = "intended_departure_at", nullable = false)
    private Instant intendedDepartureAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JourneyOutcome outcome;

    @Column(name = "outcome_at")
    private Instant outcomeAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_source", length = 30)
    private OutcomeSource outcomeSource;

    /**
     * 학생이 막차·혼자 택시를 골랐다고 기록한다(→ BUS_SELECTED / SOLO_SELECTED, 출처 USER_CHOICE).
     * 예) 매칭에 실패한 학생이 "막차로 갈게요"를 눌렀을 때.
     */
    public void chooseByUser(JourneyChoiceMode mode, Instant now) {
        this.outcome = mode.outcome();
        this.outcomeAt = now;
        this.outcomeSource = OutcomeSource.USER_CHOICE;
    }

    /**
     * 동승으로 귀가했다고 기록한다(→ POOL_BOARDED, 출처 BOARDING_CONFIRMATION).
     * 예) 채팅방에서 "탔어요"를 눌렀을 때. 학생이 귀가 선택 화면에서 직접 고를 수는 없다(api.md API-17).
     */
    public void markPoolBoarded(Instant now) {
        this.outcome = JourneyOutcome.POOL_BOARDED;
        this.outcomeAt = now;
        this.outcomeSource = OutcomeSource.BOARDING_CONFIRMATION;
    }
}
