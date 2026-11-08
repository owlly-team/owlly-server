package com.yongyong.owlly.domain.party.repository;

import com.yongyong.owlly.domain.party.entity.PartyMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
