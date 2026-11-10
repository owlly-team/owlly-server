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

    /**
     * 이 팟으로 가기로 확정한다(PENDING → ACCEPTED).
     * 예) 매칭 결과 화면에서 "이 팟 확정하기"를 누를 때. 마감·상태 확인은 서비스에서 먼저 한다.
     */
    public void confirm(Instant now) {
        this.decision = MemberDecision.ACCEPTED;
        this.confirmedAt = now;
    }

    /**
     * 팟에서 빠진다. 확정 전이면 거절(DECLINED), 확정 뒤면 이탈(LEFT).
     * 예) 매칭 결과 화면이나 채팅방에서 "빠지기"를 누를 때. 마감·송금 확인은 서비스에서 먼저 한다.
     */
    public void leave(Instant now) {
        this.decision = (decision == MemberDecision.ACCEPTED) ? MemberDecision.LEFT : MemberDecision.DECLINED;
        this.leftAt = now;
    }

    /**
     * 택시를 탔는지 기록한다(UNKNOWN → BOARDED 또는 MISSED).
     * 예) 채팅방 탑승 확인에서 "탔어요"·"못 탔어요"를 누를 때. 못 탔어요는 기록만 한다(기획 답변 2).
     */
    public void recordBoarding(BoardingStatus status, Instant now) {
        this.boardingStatus = status;
        this.boardedAt = now;
    }

    /** 아직 팟에 남아 있는 참여자인지(결정 전이거나 확정). 거절·이탈·미입금 제외는 빠진 사람이다. */
    public boolean isActive() {
        return decision == MemberDecision.PENDING || decision == MemberDecision.ACCEPTED;
    }
}
