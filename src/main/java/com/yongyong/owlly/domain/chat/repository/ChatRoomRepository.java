package com.yongyong.owlly.domain.chat.repository;

import com.yongyong.owlly.domain.chat.entity.ChatRoom;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 팟 채팅방 조회.
 *
 * <p>방은 팟당 1개(party_id UNIQUE)라, 방 ID 로 팟을 되짚을 수 있다.
 */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByPartyId(Long partyId);

    @Query("select r.partyId from ChatRoom r where r.status = com.yongyong.owlly.domain.chat.entity.RoomStatus.OPEN and r.partyId > :after order by r.partyId")
    List<Long> findOpenPartyIdsAfter(@Param("after") Long after, Pageable page);

    /**
     * 이 방이 속한 팟 ID.
     * 예) 수취 계좌 등록에서 경로의 방 ID 로 팟을 찾아, 세션 사용자가 그 팟의 참여자인지 확인할 때.
     */
    @Query("select r.partyId from ChatRoom r where r.id = :roomId")
    Optional<Long> findPartyIdById(@Param("roomId") Long roomId);
}
