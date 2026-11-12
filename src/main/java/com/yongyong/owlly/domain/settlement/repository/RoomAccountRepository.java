package com.yongyong.owlly.domain.settlement.repository;

import com.yongyong.owlly.domain.settlement.entity.AccountPurpose;
import com.yongyong.owlly.domain.settlement.entity.RoomAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 채팅방 수취 계좌 조회·저장 (API-31 등록, API-32 조회).
 *
 * <p>한 방에서 한 멤버는 용도(PREPAY/REFUND)별로 계좌가 1개다
 * (UNIQUE(room_id, owner_member_id, purpose)). 그래서 등록(PUT)은 "이미 있으면 교체,
 * 없으면 새로 저장"(upsert)인데, 그 "이미 있나?"를 이 조회로 확인한다.
 */
public interface RoomAccountRepository extends JpaRepository<RoomAccount, Long> {

    /** 같은 방·같은 멤버·같은 용도의 기존 계좌(있으면). upsert 판단에 쓴다. */
    Optional<RoomAccount> findByRoomIdAndOwnerMemberIdAndPurpose(
            Long roomId, Long ownerMemberId, AccountPurpose purpose);
}
