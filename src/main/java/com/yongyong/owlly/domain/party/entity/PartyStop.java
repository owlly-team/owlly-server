package com.yongyong.owlly.domain.party.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 버전별 하차 순서·도로 구간거리. 정렬/인원 변경 시 새 route_version 생성.
 * 실시간 이동 궤적이 아니다.
 */
@Entity
@Getter
@Table(name = "party_stops", uniqueConstraints = {
        @UniqueConstraint(name = "uk_party_stop_order", columnNames = {"party_id", "route_version", "stop_order"}),
        @UniqueConstraint(name = "uk_party_stop_member", columnNames = {"party_id", "route_version", "member_id"})
})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyStop extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> ride_parties.id */
    @Column(name = "party_id", nullable = false)
    private Long partyId;

    /** 하차 멤버 FK -> party_members.id */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "route_version", nullable = false)
    private int routeVersion;

    /** 하차 순서 1부터. */
    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    /** 직전 지점부터 이번 하차까지 도로 경로거리(m). */
    @Column(name = "segment_distance_m", nullable = false)
    private int segmentDistanceM;

    /** 경로 제공자·계산 버전. */
    @Column(name = "routing_source", nullable = false, length = 100)
    private String routingSource;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
