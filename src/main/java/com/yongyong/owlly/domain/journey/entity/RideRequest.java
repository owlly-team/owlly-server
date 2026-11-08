package com.yongyong.owlly.domain.journey.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import com.yongyong.owlly.domain.user.entity.Gender;
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

/** 각 매칭 시도의 조건 스냅샷. 동일 귀가에서 활성 신청은 최대 1개. */
@Entity
@Getter
@Table(name = "ride_requests")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RideRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 동승 신청 재시도의 멱등키. */
    @Column(name = "client_request_key", nullable = false, unique = true)
    private UUID clientRequestKey;

    /** FK -> journeys.id */
    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    /** 등록 시 소속 대학 FK -> universities.id */
    @Column(name = "university_id", nullable = false)
    private Long universityId;

    /** 출발 거점 FK -> pickup_points.id */
    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    /** 동일 방향 매칭 권역 FK -> administrative_areas.id (구 또는 시) */
    @Column(name = "district_id", nullable = false)
    private Long districtId;

    /** 하차 행정동 FK -> administrative_areas.id */
    @Column(name = "destination_dong_id", nullable = false)
    private Long destinationDongId;

    @Column(name = "dropoff_lat_encrypted", nullable = false, columnDefinition = "text")
    private String dropoffLatEncrypted;

    @Column(name = "dropoff_lng_encrypted", nullable = false, columnDefinition = "text")
    private String dropoffLngEncrypted;

    /** 날짜 포함 30분 출발 슬롯. */
    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(name = "same_gender_only", nullable = false)
    private boolean sameGenderOnly;

    /** 매칭 당시 성별 스냅샷. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_snapshot", nullable = false, length = 20)
    private Gender genderSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideRequestStatus status;

    /** 결과 안내 마감. 산정식 미확정. */
    @Column(name = "result_due_at")
    private Instant resultDueAt;

    /**
     * 매칭 대기 중인 신청을 취소한다(WAITING → CANCELLED).
     * 예) 매칭 대기 중 학생이 "등록 취소"를 누를 때. 취소할 수 있는 상태인지는 서비스에서 먼저 확인한다.
     */
    public void cancel() {
        this.status = RideRequestStatus.CANCELLED;
    }
}
