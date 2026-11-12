package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.chat.repository.ChatRoomRepository;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.settlement.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 세션 사용자(user_id) → 팟 참여(party_members.id) 해석.
 *
 * <p>계좌·정산·송금 Service 는 "팟 참여 멤버 ID"를 받는다. 하지만 인증은 user_id 만 알려주므로,
 * 방/팟/이체를 기준으로 그 사용자의 참여 멤버를 찾아 Controller 와 Service 사이를 잇는다.
 *
 * <p>참여자가 아니면 404 로 숨긴다(남의 방·팟·이체 존재 여부를 노출하지 않는다).
 * 용도별 대표/본인 구분, 방 만료 등 세부 권한은 각 Service 의 정책으로 남겨 둔다.
 */
@Component
@RequiredArgsConstructor
public class SettlementMemberResolver {

    private final PartyMemberRepository partyMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final TransferRepository transferRepository;

    /** 이 팟에서 사용자의 참여 멤버 ID. 참여자가 아니면 404. */
    public Long memberOfParty(Long partyId, Long userId) {
        return partyMemberRepository.findMemberIdByPartyIdAndUserId(partyId, userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 이 채팅방이 속한 팟에서 사용자의 참여 멤버 ID. 방이 없거나 참여자가 아니면 404. */
    public Long memberOfRoom(Long roomId, Long userId) {
        Long partyId = chatRoomRepository.findPartyIdById(roomId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        return memberOfParty(partyId, userId);
    }

    /** 이 이체가 속한 팟에서 사용자의 참여 멤버 ID. 이체가 없거나 참여자가 아니면 404. */
    public Long memberOfTransferParty(Long transferId, Long userId) {
        Long partyId = transferRepository.findPartyIdById(transferId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        return memberOfParty(partyId, userId);
    }
}
