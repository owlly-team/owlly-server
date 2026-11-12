package com.yongyong.owlly.domain.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.*;
import com.yongyong.owlly.common.util.Masking;
import com.yongyong.owlly.domain.chat.dto.*;
import com.yongyong.owlly.domain.chat.entity.*;
import com.yongyong.owlly.domain.chat.repository.*;
import com.yongyong.owlly.domain.party.entity.*;
import com.yongyong.owlly.domain.party.repository.*;
import com.yongyong.owlly.domain.party.service.EncryptedValueReader;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.settlement.entity.AccountPurpose;
import com.yongyong.owlly.domain.settlement.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팟 행 잠금으로 생성·전송·시스템 이벤트·이탈·종료 순서를 통일한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final ChatSendReceiptRepository receipts;
    private final RidePartyRepository parties;
    private final PartyMemberRepository members;
    private final PickupPointRepository pickups;
    private final RoomAccountRepository accounts;
    private final SettlementRepository settlements;
    private final ChatTextMasker masker;
    private final EncryptedValueReader encryptedValues;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    @Value("${owlly.verification.secret:}") private String secret;

    /** API-23 확정 트랜잭션에서 호출. 대표의 계좌 등록에도 필요한 내부 방을 준비한다. */
    public Long openRoom(Long partyId) {
        RideParty party = lockParty(partyId);
        if (party.getStatus() == PartyStatus.CANCELLED) throw new OwllyException(ErrorCode.ROOM_CLOSED);
        var existing = rooms.findByPartyId(partyId);
        if (existing.isPresent()) return existing.get().getId();
        if (members.findByPartyIdOrderByIdAsc(partyId).stream().noneMatch(m -> m.getDecision() == MemberDecision.ACCEPTED))
            throw new OwllyException(ErrorCode.ROOM_NOT_READY);
        ChatRoom room = rooms.saveAndFlush(ChatRoom.builder().partyId(partyId).openedAt(Instant.now()).status(RoomStatus.OPEN).build());
        long count = members.findByPartyIdOrderByIdAsc(partyId).stream().filter(PartyMember::isActive).count();
        system(room, "opened", "매칭이 확정됐어요 · " + count + "명");
        system(room, "privacy", "전화번호 · 집 주소는 주고받지 마세요. 번호 · 링크 · 계좌번호는 자동으로 가려져요.");
        return room.getId();
    }

    public RoomResponse getRoom(Long userId, Long partyId, Instant now) {
        RideParty party = lockParty(partyId);
        Long memberId = acceptedUser(partyId, userId);
        ChatRoom room = rooms.findByPartyId(partyId).orElseThrow(() -> new OwllyException(ErrorCode.ROOM_NOT_READY));
        requireOpen(party, room, now, false);
        requireRepresentativeAccount(party, room, memberId);
        var pickup = pickups.findById(party.getPickupPointId()).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        return new RoomResponse(room.getId(), partyId, room.getStatus(), pickup.getMeetingNote(), party.getDepartureAt(),
                encryptedValues.readText(party.getVerificationCodeEncrypted()), room.getClosedAt(), room.getDeleteAfter(), true);
    }

    public MessagePage getMessages(Long userId, Long roomId, Long after, Long before, int limit, Instant now) {
        if (after != null && before != null || limit < 1 || limit > 100) throw new OwllyException(ErrorCode.VALIDATION_ERROR);
        access(userId, roomId, now, false);
        var page = PageRequest.of(0, limit + 1);
        List<ChatMessage> found = after != null ? messages.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, after, page)
                : before != null ? messages.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, before, page)
                : messages.findByRoomIdOrderByIdDesc(roomId, page);
        boolean more = found.size() > limit;
        var result = found.stream().limit(limit).map(MessageResponse::of).toList();
        Long latest = result.stream().map(MessageResponse::id).max(Long::compareTo).orElse(null);
        Long next = after == null && more ? result.getLast().id() : null;
        return new MessagePage(result, new MessagePage.MessagePageMeta(next, latest, more));
    }

    public MessageResponse send(Long userId, Long roomId, MessageCreate input, String key, Instant now) {
        String hash = fingerprint(userId, roomId, input);
        UUID clientId = UUID.fromString(input.clientMessageId());
        // 키 → client UUID → 팟 순서. 서로 다른 방에서도 같은 UUID의 충돌은 직렬화한다.
        lock("chat-key:" + key);
        lock("chat-client:" + clientId);
        Long memberId = access(userId, roomId, now, true);
        var prior = receipts.findById(key);
        if (prior.isPresent()) {
            requireSame(hash, prior.get().getRequestHash());
            return MessageResponse.of(messages.findById(prior.get().getMessageId())
                    .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND)));
        }
        var sameClient = messages.findByClientMessageId(clientId);
        ChatMessage message;
        if (sameClient.isPresent()) {
            message = sameClient.get();
            requireSame(hash, message.getRequestHash());
        } else {
            String body = masker.mask(input.text());
            if (body.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp)))
                throw new OwllyException(ErrorCode.MESSAGE_REJECTED);
            message = messages.saveAndFlush(ChatMessage.builder().roomId(roomId).senderMemberId(memberId)
                    .messageType(MessageType.USER).body(body).clientMessageId(clientId).requestHash(hash).build());
        }
        receipts.save(new ChatSendReceipt(key, hash, message.getId()));
        return MessageResponse.of(message);
    }

    /** 계좌 API는 대표의 채팅 입장 전에도 필요하므로 계좌 등록 여부는 여기서 검사하지 않는다. */
    public void requireAccountAccess(Long roomId, Long memberId, Instant now) {
        requireAccountAccess(roomId, memberId, now, ErrorCode.ACCOUNT_EXPIRED);
    }

    public void requireAccountRegistration(Long roomId, Long memberId, Instant now) {
        requireAccountAccess(roomId, memberId, now, ErrorCode.ROOM_CLOSED);
    }

    /** 종료 정리로 이체의 계좌 참조가 이미 NULL이어도 미등록(409)과 만료(410)를 구분한다. */
    public void requireTransferAccountAccess(Long partyId, Long memberId, Instant now) {
        lockParty(partyId);
        ChatRoom room = rooms.findByPartyId(partyId).orElseThrow(() -> new OwllyException(ErrorCode.ACCOUNT_EXPIRED));
        requireAccountAccess(room.getId(), memberId, now, ErrorCode.ACCOUNT_EXPIRED);
    }

    private void requireAccountAccess(Long roomId, Long memberId, Instant now, ErrorCode expired) {
        Long partyId = rooms.findPartyIdById(roomId).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        var party = lockParty(partyId);
        var room = rooms.findById(roomId).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        members.findById(memberId).filter(m -> m.getPartyId().equals(party.getId()) && m.getDecision() == MemberDecision.ACCEPTED)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (closed(party, room, now)) throw new OwllyException(expired);
    }

    public void memberLeft(Long partyId, Long memberId) {
        lockParty(partyId);
        rooms.findByPartyId(partyId).filter(r -> r.getStatus() == RoomStatus.OPEN).ifPresent(room ->
                system(room, "left:" + memberId, memberName(partyId, memberId) + "님이 나갔어요"));
    }

    public void taxiCalled(Long partyId) {
        RideParty party = lockParty(partyId);
        rooms.findByPartyId(partyId).filter(r -> r.getStatus() == RoomStatus.OPEN).ifPresent(room ->
                system(room, "taxi-called", memberName(partyId, party.getRepresentativeMemberId())
                        + "님이 택시를 불렀어요 · 타면 탔어요를 눌러 주세요"));
    }

    public void closeRoom(Long partyId, Instant now) {
        lockParty(partyId);
        rooms.findByPartyId(partyId).ifPresent(room -> close(room, now));
    }

    /** 스케줄러는 팟별 트랜잭션으로 호출한다. HTTP 요청도 동일한 종료 조건을 즉시 검사한다. */
    public void synchronizeRoom(Long partyId, Instant now) {
        RideParty party = lockParty(partyId);
        var room = rooms.findByPartyId(partyId).orElse(null);
        if (room == null || room.getStatus() == RoomStatus.CLOSED) return;
        Instant closeAt = closeAt(party);
        if (party.getStatus() == PartyStatus.CANCELLED || closeAt != null && !now.isBefore(closeAt)) {
            close(room, party.getStatus() == PartyStatus.CANCELLED ? now : closeAt);
            return;
        }
        if (!now.isBefore(party.getDepartureAt().minusSeconds(600)) && now.isBefore(party.getDepartureAt()))
            system(room, "gathering", "집결 10분 전이에요 · 대표 "
                    + memberName(partyId, party.getRepresentativeMemberId()) + "님이 택시를 불러요");
    }

    private void close(ChatRoom room, Instant when) {
        if (room.getStatus() == RoomStatus.CLOSED) return;
        room.close(when);
        // 계좌 원문은 삭제하고 이체 금액·송수신 확인 이력은 유지한다.
        jdbc.update("update transfers set receiver_account_id=null where receiver_account_id in (select id from room_accounts where room_id=?)", room.getId());
        jdbc.update("delete from room_accounts where room_id=?", room.getId());
    }

    /** 신고도 메시지 전송과 같은 현재 멤버·계좌·방 수명 검사를 거치며 팟 잠금을 공유한다. */
    public Long requireReportAccess(Long userId, Long roomId) {
        return access(userId, roomId, null, true);
    }

    private Long access(Long userId, Long roomId, Instant now, boolean sending) {
        var partyId = rooms.findPartyIdById(roomId).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        RideParty party = lockParty(partyId);
        Long memberId = acceptedUser(partyId, userId);
        ChatRoom room = rooms.findById(roomId).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOpen(party, room, now == null ? Instant.now() : now, sending);
        requireRepresentativeAccount(party, room, memberId);
        return memberId;
    }

    private Long acceptedUser(Long partyId, Long userId) {
        Long id = members.findMemberIdsByPartyIdAndUserId(partyId, userId)
                .stream().findFirst()
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        var member = members.findById(id).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (member.getDecision() != MemberDecision.ACCEPTED) throw new OwllyException(ErrorCode.FORBIDDEN);
        return id;
    }

    private void requireRepresentativeAccount(RideParty party, ChatRoom room, Long memberId) {
        if (memberId.equals(party.getRepresentativeMemberId())
                && accounts.findByRoomIdAndOwnerMemberIdAndPurpose(room.getId(), memberId, AccountPurpose.PREPAY_RECEIVE).isEmpty())
            throw new OwllyException(ErrorCode.ROOM_NOT_READY);
    }

    private void requireOpen(RideParty party, ChatRoom room, Instant now, boolean sending) {
        if (closed(party, room, now)) throw new OwllyException(sending ? ErrorCode.ROOM_CLOSED : ErrorCode.ROOM_ACCESS_EXPIRED);
    }

    private boolean closed(RideParty party, ChatRoom room, Instant now) {
        Instant deadline = closeAt(party);
        return room.getStatus() == RoomStatus.CLOSED || party.getStatus() == PartyStatus.CANCELLED
                || deadline != null && !now.isBefore(deadline);
    }

    private Instant closeAt(RideParty party) {
        Instant base = party.getDepartureAt().plusSeconds(3600);
        // v0.4는 대표 정산 확정을 정산 완료로 정의한다. 외부 송금 수령까지 기다리는 정책은 추가하지 않는다.
        Instant finalized = settlements.findByPartyId(party.getId()).map(s -> s.getFinalizedAt()).orElse(null);
        if (finalized == null) return null; // 정산 생성 전·미확정도 미완료이므로 조기 종료하지 않는다.
        return finalized.isAfter(base) ? finalized : base;
    }

    private RideParty lockParty(Long id) {
        return parties.findByIdForUpdate(id).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String memberName(Long partyId, Long memberId) {
        return members.findRowsByPartyId(partyId).stream().filter(r -> r.memberId().equals(memberId))
                .map(r -> Masking.holderName(encryptedValues.readText(r.realNameEncrypted()))).findFirst().orElse("멤버");
    }

    private void system(ChatRoom room, String event, String body) {
        String eventKey = room.getId() + ":" + event;
        if (messages.findBySystemEventKey(eventKey).isPresent()) return;
        messages.saveAndFlush(ChatMessage.builder().roomId(room.getId()).messageType(MessageType.SYSTEM)
                .body(body).clientMessageId(UUID.randomUUID()).systemEventKey(eventKey).build());
    }

    private String fingerprint(Long userId, Long roomId, MessageCreate input) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String body = mapper.writeValueAsString(List.of(userId, roomId, UUID.fromString(input.clientMessageId()).toString(), input.text()));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | JsonProcessingException e) { throw new IllegalStateException("채팅 요청 식별값 생성 실패"); }
    }

    private void requireSame(String hash, String saved) {
        if (saved == null || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), saved.getBytes(StandardCharsets.UTF_8)))
            throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    private void lock(String key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", key);
    }
}
