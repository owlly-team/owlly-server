package com.yongyong.owlly.domain.party.repository;

import com.yongyong.owlly.domain.party.dto.PartyMemberRow;
import com.yongyong.owlly.domain.party.entity.PartyMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 팟 참여자 조회.
 *
 * <p>한 동승 신청은 최대 한 팟에 배정된다(request_id UNIQUE).
 */
public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {

    /**
     * 이 신청이 배정된 팟 참여 정보.
     * 예) 매칭 대기 카드에서 결과를 확인할 때, 매칭됐다면 어느 팟인지(partyId) 찾아 매칭 결과 화면으로 보낸다.
     */
    Optional<PartyMember> findByRequestId(Long requestId);

    /**
     * 팟의 참여자 전원(팟에 들어온 순서).
     * 예) 팟 확정 때 남은 멤버가 전원 확정했는지 확인할 때.
     */
    List<PartyMember> findByPartyIdOrderByIdAsc(Long partyId);

    /**
     * 팟 참여자 전원을 학생 프로필·학교·하차 동과 함께 한 번에 읽는다(팟에 들어온 순서).
     * 참여에는 학생 번호가 없어서 참여 → 신청 → 귀가 → 학생 프로필 순서로 잇는다.
     * 예) 매칭 결과 화면의 멤버 목록(이름 일부·학교·하차 동·동성 여부)과 "내가 누구인지" 찾기.
     */
    /**
     * 이 팟에서 해당 사용자의 참여(party_members.id).
     * 참여에는 user_id 가 없어서 참여 -> 신청 -> 귀가 로 사용자를 잇는다.
     * 예) 세션 사용자가 이 팟의 참여자인지 확인하고, 그의 멤버 ID 로 계좌·송금 권한을 판단할 때.
     */
    @Query("""
            select m.id from PartyMember m
            join RideRequest r on r.id = m.requestId
            join Journey j on j.id = r.journeyId
            where m.partyId = :partyId and j.userId = :userId
            """)
    Optional<Long> findMemberIdByPartyIdAndUserId(@Param("partyId") Long partyId, @Param("userId") Long userId);

    /** 신고 대상의 사용자 ID. 프로필 표시 정보에 의존하지 않는다. */
    @Query("""
            select j.userId from PartyMember m
            join RideRequest r on r.id = m.requestId
            join Journey j on j.id = r.journeyId
            where m.id = :memberId
            """)
    Optional<Long> findUserIdByMemberId(@Param("memberId") Long memberId);

    @Query("""
            select new com.yongyong.owlly.domain.party.dto.PartyMemberRow(
                m.id, j.userId, m.decision, m.boardingStatus, r.genderSnapshot,
                p.realNameEncrypted, u.name, a.name)
            from PartyMember m
            join RideRequest r on r.id = m.requestId
            join Journey j on j.id = r.journeyId
            join StudentProfile p on p.userId = j.userId
            join University u on u.id = r.universityId
            join AdministrativeArea a on a.id = r.destinationDongId
            where m.partyId = :partyId
            order by m.id asc
            """)
    List<PartyMemberRow> findRowsByPartyId(@Param("partyId") Long partyId);
}
