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
 * 팟 참여·확정·탑승 상태. 한 신청(request_id)은 최대 한 팟에 배정(UNIQUE).
 * user_id 는 request -> journey 로 구한다.
 */
@Entity
@Getter
@Table(name = "party_members")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> ride_parties.id */
    @Column(name = "party_id", nullable = false)
    private Long partyId;

    /** 배정된 매칭 신청 FK -> ride_requests.id. UNIQUE. */
    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "boarding_status", nullable = false, length = 20)
    private BoardingStatus boardingStatus;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "boarded_at")
    private Instant boardedAt;
}
