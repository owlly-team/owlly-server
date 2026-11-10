package com.yongyong.owlly.domain.party.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 형성된 2~4인 동승 팟. 대표는 마지막 하차자이며 UNIQUE(representative_member_id).
 * 생성 순서: 팟 생성 -> 멤버 생성 -> 대표 지정(순환 참조 회피).
 */
@Entity
@Getter
@Table(name = "ride_parties")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RideParty extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> universities.id */
    @Column(name = "university_id", nullable = false)
    private Long universityId;

    /** 집결 거점 FK -> pickup_points.id */
    @Column(name = "pickup_point_id", nullable = false)
    private Long pickupPointId;

    /** 매칭 권역 FK -> administrative_areas.id */
    @Column(name = "district_id", nullable = false)
    private Long districtId;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    /** 마지막 하차자 FK -> party_members.id. 구성 확정 전 NULL. UNIQUE. */
    @Column(name = "representative_member_id", unique = true)
    private Long representativeMemberId;

    /** 같은 팟 확인용 4자리 코드 (암호화). */
    @Column(name = "verification_code_encrypted", nullable = false, columnDefinition = "text")
    private String verificationCodeEncrypted;

    /** 현재 하차 순서·예상 경로 버전. */
    @Column(name = "route_version", nullable = false)
    private int routeVersion;

    @Column(name = "confirm_deadline_at")
    private Instant confirmDeadlineAt;

    /** 대표가 '택시를 불렀어요'를 누른 시각. 실제 호출 API 연동 아님. */
    @Column(name = "taxi_called_at")
    private Instant taxiCalledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyStatus status;

    /**
     * 마지막 하차자를 대표로 정하고, 멤버들의 확정을 기다리는 상태로 바꾼다(FORMING → CONFIRMING).
     * 팟 생성 → 멤버 생성 → 대표 지정 순서로 저장해야 해서(순환 참조) 멤버를 저장한 뒤에 부른다.
     */
    public void assignRepresentative(Long memberId) {
        this.representativeMemberId = memberId;
        this.status = PartyStatus.CONFIRMING;
    }

    /**
     * 확정이 끝나 선송금 단계로 넘어간다(CONFIRMING → PREPAYING).
     * 예) 남은 멤버가 전원 확정해서 채팅방이 열릴 때. 이후 멤버는 대표에게 선송금을 보낸다.
     */
    public void startPrepaying() {
        this.status = PartyStatus.PREPAYING;
    }
}
