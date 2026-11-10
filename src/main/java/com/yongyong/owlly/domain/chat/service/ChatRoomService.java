package com.yongyong.owlly.domain.chat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팟 채팅방 열기 (호출 껍데기).
 *
 * <p>상황: 팟 멤버가 전원 확정하거나 확정 마감이 되면 확정한 사람끼리 채팅방이 열린다(화면정의서 S-05 화면 정책).
 * 팟 확정과 확정 마감 처리가 같은 트랜잭션에서 부른다.
 *
 * <p>내용은 채팅 담당(나연님)이 채움. 채우기 전까지는 방을 만들지 않고 null 을 돌려주므로
 * 팟 조회의 roomId 도 null 이다.
 */
@Service
public class ChatRoomService {

    /**
     * 팟의 채팅방을 연다. 팟당 방은 1개라 이미 있으면 그 방을 돌려준다.
     *
     * @param partyId 확정이 끝난 팟
     * @return 열린 방 번호. 아직 구현 전이면 null
     */
    @Transactional
    public Long openRoom(Long partyId) {
        return null;
    }
}
