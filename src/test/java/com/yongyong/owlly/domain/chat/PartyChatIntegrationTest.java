package com.yongyong.owlly.domain.chat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.*;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.domain.chat.dto.*;
import com.yongyong.owlly.domain.chat.entity.*;
import com.yongyong.owlly.domain.chat.repository.*;
import com.yongyong.owlly.domain.chat.service.ChatRoomService;
import com.yongyong.owlly.domain.journey.entity.*;
import com.yongyong.owlly.domain.journey.repository.*;
import com.yongyong.owlly.domain.party.entity.*;
import com.yongyong.owlly.domain.party.repository.*;
import com.yongyong.owlly.domain.party.service.PartyService;
import com.yongyong.owlly.domain.region.entity.*;
import com.yongyong.owlly.domain.region.repository.*;
import com.yongyong.owlly.domain.settlement.entity.*;
import com.yongyong.owlly.domain.settlement.repository.*;
import com.yongyong.owlly.domain.settlement.service.RoomAccountService;
import com.yongyong.owlly.domain.settlement.dto.AccountInput;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 잠금·세션 보안·암호화·DB 쓰기는 실제 구현으로 검증한다. */
@SpringBootTest(properties = {"owlly.matching.enabled=false", "owlly.chat.scheduling-enabled=false",
        "owlly.verification.secret=01234567890123456789012345678901"})
@AutoConfigureMockMvc
class PartyChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatRoomService chat;
    @Autowired PartyService partyService;
    @Autowired RoomAccountService accountService;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMessageRepository messages;
    @MockitoSpyBean ChatSendReceiptRepository receipts;
    @Autowired RidePartyRepository parties;
    @Autowired PartyMemberRepository members;
    @Autowired JourneyRepository journeys;
    @Autowired RideRequestRepository rides;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired UniversityRepository universities;
    @Autowired AdministrativeAreaRepository areas;
    @Autowired PickupPointRepository pickups;
    @Autowired SettlementRepository settlements;
    @Autowired SettlementItemRepository settlementItems;
    @Autowired TransferRepository transfers;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RoomAccountRepository accounts;
    @Autowired TextCryptor cryptor;
    User student, representative, outsider;
    AdministrativeArea city, dong;
    PickupPoint pickup;
    RideParty party;
    PartyMember me, rep;
    Instant departure;
    Long roomId;
    final List<Long> journeyIds = new ArrayList<>();
    final List<Long> rideIds = new ArrayList<>();

    @BeforeEach void setup() {
        // PostgreSQL timestamptz는 마이크로초 정밀도이므로 경계 시각을 DB에 정확히 보존한다.
        departure = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusSeconds(7200);
        Long university = universities.findByActiveTrueAndNameInOrderByIdAsc(List.of("한국외국어대학교")).getFirst().getId();
        city = area("채팅테스트시", AreaLevel.CITY, null);
        dong = area("채팅테스트동", AreaLevel.DONG, city.getId());
        pickup = pickups.saveAndFlush(PickupPoint.builder().universityId(university).areaId(city.getId()).name("채팅테스트정문")
                .meetingNote("정문 오른쪽 편의점 앞").latitude(new BigDecimal("37.3")).longitude(new BigDecimal("127.1")).active(true).build());
        student = user(university, Role.STUDENT, "한서윤");
        representative = user(university, Role.STUDENT, "이서준");
        outsider = user(university, Role.STUDENT, "김가영");
        party = parties.saveAndFlush(RideParty.builder().universityId(university).pickupPointId(pickup.getId()).districtId(city.getId())
                .departureAt(departure).confirmDeadlineAt(departure.minusSeconds(900)).verificationCodeEncrypted(cryptor.encrypt("0042"))
                .routeVersion(1).status(PartyStatus.FORMING).build());
        me = member(student, MemberDecision.ACCEPTED);
        rep = member(representative, MemberDecision.PENDING);
        party.assignRepresentative(rep.getId());
        parties.saveAndFlush(party);
        roomId = chat.openRoom(party.getId());
    }

    @AfterEach void cleanup() {
        reset(receipts);
        jdbc.update("delete from chat_send_receipts where message_id in (select id from chat_messages where room_id=?)", roomId);
        jdbc.update("delete from chat_messages where room_id=?", roomId);
        jdbc.update("delete from room_accounts where room_id=?", roomId);
        jdbc.update("delete from chat_rooms where party_id=?", party.getId());
        jdbc.update("delete from notifications where party_id=?", party.getId());
        jdbc.update("delete from transfers where settlement_item_id in (select i.id from settlement_items i join settlements s on s.id=i.settlement_id where s.party_id=?)", party.getId());
        jdbc.update("delete from settlement_items where settlement_id in (select id from settlements where party_id=?)", party.getId());
        jdbc.update("delete from settlements where party_id=?", party.getId());
        jdbc.update("update ride_parties set representative_member_id=null where id=?", party.getId());
        jdbc.update("delete from party_members where party_id=?", party.getId());
        parties.deleteById(party.getId());
        rideIds.forEach(rides::deleteById);
        journeyIds.forEach(journeys::deleteById);
        for (User user : List.of(student, representative, outsider)) {
            jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name=?)", user.getId().toString());
            jdbc.update("delete from spring_session where principal_name=?", user.getId().toString());
            profiles.deleteById(user.getId()); users.deleteById(user.getId());
        }
        pickups.deleteById(pickup.getId()); areas.deleteById(dong.getId()); areas.deleteById(city.getId());
    }

    @Test void confirmedMemberEntersBeforeOthersAndRoomCreationIsIdempotent() throws Exception {
        assertThat(chat.openRoom(party.getId())).isEqualTo(roomId);
        mvc.perform(get(roomPath()).with(authentication(auth(student)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(roomId.toString()))
                .andExpect(jsonPath("$.data.verificationCode").value("0042"))
                .andExpect(jsonPath("$.data.meetingNote").value("정문 오른쪽 편의점 앞"))
                .andExpect(jsonPath("$.data.canSendMessage").value(true)).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(2);
    }

    @Test void representativeMustRegisterAccountButCanObtainInternalRoomIdFromParty() throws Exception {
        acceptRep();
        mvc.perform(get("/api/v1/parties/" + party.getId()).with(authentication(auth(representative))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roomId").value(roomId.toString()));
        mvc.perform(get(roomPath()).with(authentication(auth(representative)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_READY"));
        accountService.register(roomId, AccountPurpose.PREPAY_RECEIVE, rep.getId(), new AccountInput("004", "12345678901", "이서준"));
        mvc.perform(get(roomPath()).with(authentication(auth(representative)))).andExpect(status().isOk());
    }

    @Test void deniesAnonymousOtherPartyPendingLeftStaffAndSuspended() throws Exception {
        mvc.perform(get(roomPath())).andExpect(status().isUnauthorized());
        for (User user : List.of(outsider, representative)) {
            int expected = user == outsider ? 404 : 403;
            mvc.perform(get(roomPath()).with(authentication(auth(user)))).andExpect(status().is(expected));
            mvc.perform(get(messagePath()).with(authentication(auth(user)))).andExpect(status().is(expected));
            postMessage(user, "도착", UUID.randomUUID().toString(), key()).andExpect(status().is(expected));
        }
        jdbc.update("update party_members set decision='LEFT' where id=?", me.getId());
        mvc.perform(get(messagePath()).with(authentication(auth(student)))).andExpect(status().isForbidden());
        jdbc.update("update users set role='STAFF' where id=?", outsider.getId());
        mvc.perform(get(messagePath()).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        jdbc.update("update users set status='SUSPENDED' where id=?", student.getId());
        mvc.perform(get(messagePath()).with(authentication(auth(student)))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test void masksBeforePersistenceAndPreservesMeetingCodeAndServerSender() throws Exception {
        String client = UUID.randomUUID().toString();
        var result = postMessage(student, "0042 맞아요 010-1234-5678 https://example.com 123-456789-01", client, key())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.senderMemberId").value(me.getId().toString()))
                .andExpect(jsonPath("$.data.messageType").value("USER")).andReturn();
        String body = mapper.readTree(result.getResponse().getContentAsString()).at("/data/body").asText();
        assertThat(body).contains("0042 맞아요", "가려진 번호").doesNotContain("010", "example.com", "456789");
        var stored = messages.findByClientMessageId(UUID.fromString(client)).orElseThrow();
        assertThat(stored.getBody()).isEqualTo(body);
        assertThat(stored.getRequestHash()).hasSize(64).doesNotContain("010-1234");
    }

    @Test void validatesBodyTypesHeadersLengthAndCsrf() throws Exception {
        for (String text : List.of("", " ", "가".repeat(201))) postMessage(student, text, UUID.randomUUID().toString(), key())
                .andExpect(status().isUnprocessableEntity());
        postMessage(student, "가".repeat(200), UUID.randomUUID().toString(), key()).andExpect(status().isCreated());
        for (String body : List.of("{}", "{\"clientMessageId\":\"bad\",\"text\":\"도착\"}",
                "{\"clientMessageId\":\"" + key() + "\",\"text\":123}",
                "{\"clientMessageId\":\"" + key() + "\",\"text\":\"도착\",\"messageType\":\"SYSTEM\"}",
                "{\"clientMessageId\":\"" + key() + "\",\"text\":\"도착\",\"senderMemberId\":\"1\"}")) {
            mvc.perform(post(messagePath()).with(authentication(auth(student))).with(csrf().asHeader())
                    .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(post(messagePath()).with(authentication(auth(student))).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body("도착", key()))).andExpect(status().isForbidden());
        mvc.perform(post(messagePath()).with(authentication(auth(student))).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(body("도착", key()))).andExpect(status().isUnprocessableEntity());
        postMessage(student, "도착", key(), "bad").andExpect(status().isUnprocessableEntity());
        postMessage(student, "\u200b", key(), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MESSAGE_REJECTED"));
    }

    @Test void sameKeysAndClientIdsReturnOneMessageButConflictingRawPayloadsFail() throws Exception {
        String key = key(), client = key();
        var initial = postMessage(student, "010-1234-5678", client, key).andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(initial.getResponse().getContentAsString()).at("/data/id").asText();
        postMessage(student, "010-1234-5678", client.toUpperCase(Locale.ROOT), key.toUpperCase(Locale.ROOT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        postMessage(student, "010-1234-5678", client, key()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        postMessage(student, "010-9999-9999", client, key()).andExpect(status().isConflict());
        postMessage(student, "다른 내용", key(), key).andExpect(status().isConflict());
        acceptRep(); registerRepAccount();
        postMessage(representative, "010-1234-5678", client, key).andExpect(status().isConflict());
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(3);
    }

    @Test void pagesInitialAndHistoricalDescendingAndNewMessagesAscendingWithoutSkips() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(send("메시지" + i).id());
        var first = chat.getMessages(student.getId(), roomId, null, null, 2, Instant.now());
        assertThat(first.data()).extracting(MessageResponse::id).containsExactly(ids.get(4), ids.get(3));
        assertThat(first.meta().nextBeforeId()).isEqualTo(ids.get(3));
        var older = chat.getMessages(student.getId(), roomId, null, first.meta().nextBeforeId(), 2, Instant.now());
        assertThat(older.data()).extracting(MessageResponse::id).containsExactly(ids.get(2), ids.get(1));
        var newer = chat.getMessages(student.getId(), roomId, ids.getFirst(), null, 2, Instant.now());
        assertThat(newer.data()).extracting(MessageResponse::id).containsExactly(ids.get(1), ids.get(2));
        assertThat(newer.meta().hasMore()).isTrue();
        assertThat(newer.meta().latestId()).isEqualTo(ids.get(2));
        assertThat(newer.meta().nextBeforeId()).isNull();
        mvc.perform(get(messagePath()).param("afterId", "1").param("beforeId", "2").with(authentication(auth(student))))
                .andExpect(status().isUnprocessableEntity());
        for (String value : List.of("0", "101", "bad")) mvc.perform(get(messagePath()).param("limit", value).with(authentication(auth(student))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(messagePath()).param("afterId", "-1").with(authentication(auth(student)))).andExpect(status().isUnprocessableEntity());
    }

    @Test void concurrentRetriesCreateOneUserMessage() throws Exception {
        String key = key(), client = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> action = () -> { start.await(); return chat.send(student.getId(), roomId, new MessageCreate(client, "동시 요청"), key, Instant.now()).id(); };
            var a = pool.submit(action); var b = pool.submit(action); start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(3);
    }

    @Test void failedReceiptPersistenceRollsBackMessageAndAllowsSameRetry() {
        String key = key(), client = key();
        doThrow(new IllegalStateException("storage unavailable")).when(receipts).save(any());
        assertThatThrownBy(() -> chat.send(student.getId(), roomId, new MessageCreate(client, "도착"), key, Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThat(messages.findByClientMessageId(UUID.fromString(client))).isEmpty();
        reset(receipts);
        assertThat(chat.send(student.getId(), roomId, new MessageCreate(client, "도착"), key, Instant.now()).body()).isEqualTo("도착");
    }

    @Test void simultaneousRoomCreationSeedsOneRoomAndOneSetOfSystemMessages() throws Exception {
        jdbc.update("delete from chat_messages where room_id=?", roomId);
        rooms.deleteById(roomId);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> open = () -> { start.await(); return chat.openRoom(party.getId()); };
            var first = pool.submit(open); var second = pool.submit(open); start.countDown();
            roomId = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(roomId);
        }
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(2);
    }

    @Test void concurrentDifferentKeysWithSameClientIdStillCreateOneMessage() throws Exception {
        String client = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> action = () -> { start.await(); return chat.send(student.getId(), roomId, new MessageCreate(client, "재전송"), key(), Instant.now()).id(); };
            var first = pool.submit(action); var second = pool.submit(action); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(3);
    }

    @Test void sendWaitsForMembershipChangeAndChecksCommittedDecision() throws Exception {
        String client = key();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            var leave = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                parties.findByIdForUpdate(party.getId()).orElseThrow();
                var current = members.findById(me.getId()).orElseThrow();
                current.leave(Instant.now()); members.flush(); locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return true;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var send = pool.submit(() -> chat.send(student.getId(), roomId, new MessageCreate(client, "이미 이탈"), key(), Instant.now()));
            try { assertThatThrownBy(() -> send.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            assertThat(leave.get(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> send.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(OwllyException.class)
                    .satisfies(e -> assertThat(((OwllyException) e.getCause()).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
        assertThat(messages.findByClientMessageId(UUID.fromString(client))).isEmpty();
    }

    @Test void actualCookieAndSessionCsrfCanSendMessage() throws Exception {
        var initial = mvc.perform(get(roomPath()).with(authentication(auth(student)))).andExpect(status().isOk()).andReturn();
        var cookie = initial.getResponse().getCookie("owlly_session");
        assertThat(cookie).isNotNull();
        var session = mvc.perform(get("/api/v1/auth/session").cookie(cookie)).andExpect(status().isOk()).andReturn();
        String csrf = mapper.readTree(session.getResponse().getContentAsString()).at("/data/csrfToken").asText();
        mvc.perform(post(messagePath()).cookie(cookie).header("X-CSRF-Token", csrf).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body("쿠키 로그인 전송", key())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.senderMemberId").value(me.getId().toString()));
    }

    @Test void systemEventsAreOnceOnlyAndTaxiHookUsesMaskedName() {
        acceptRep(); registerRepAccount();
        party.startPrepaying(); parties.saveAndFlush(party);
        partyService.confirmTaxiCall(representative.getId(), party.getId(), Instant.now(), null);
        partyService.confirmTaxiCall(representative.getId(), party.getId(), Instant.now(), null);
        chat.synchronizeRoom(party.getId(), departure.minusSeconds(601));
        assertThat(messages.findBySystemEventKey(roomId + ":gathering")).isEmpty();
        chat.synchronizeRoom(party.getId(), departure.minusSeconds(600));
        chat.synchronizeRoom(party.getId(), departure.minusSeconds(500));
        chat.memberLeft(party.getId(), me.getId()); chat.memberLeft(party.getId(), me.getId());
        assertThat(messages.findBySystemEventKey(roomId + ":taxi-called").orElseThrow().getBody()).contains("이*준").doesNotContain("이서준");
        assertThat(messages.findBySystemEventKey(roomId + ":gathering")).isPresent();
        assertThat(messages.findBySystemEventKey(roomId + ":left:" + me.getId())).isPresent();
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(5);
    }

    @Test void roomWaitsForSettlementThenClosesAtLaterOfDeparturePlusHourAndFinalization() {
        chat.synchronizeRoom(party.getId(), departure.plusSeconds(3600));
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.OPEN);
        Instant finalized = departure.plusSeconds(5000);
        settlements.saveAndFlush(Settlement.builder().partyId(party.getId()).estimatedTotalWon(10000).actualTotalWon(10000)
                .routeVersion(1).status(SettlementStatus.COMPLETED).finalizedAt(finalized).build());
        assertThat(chat.getRoom(student.getId(), party.getId(), finalized.minusMillis(1))).isNotNull();
        assertError(() -> chat.getRoom(student.getId(), party.getId(), finalized), ErrorCode.ROOM_ACCESS_EXPIRED);
        chat.synchronizeRoom(party.getId(), finalized);
        assertThat(rooms.findById(roomId).orElseThrow().getClosedAt()).isEqualTo(finalized);
        assertThat(rooms.findById(roomId).orElseThrow().getDeleteAfter()).isNull();
        assertError(() -> chat.send(student.getId(), roomId, new MessageCreate(key(), "늦은 메시지"), key(), finalized), ErrorCode.ROOM_CLOSED);
    }

    @Test void earlySettlementStillKeepsRoomUntilHourAndClosureDeletesAccountsButNotMessages() {
        acceptRep(); registerRepAccount();
        var settlement = settlements.saveAndFlush(Settlement.builder().partyId(party.getId()).estimatedTotalWon(10000).routeVersion(1)
                .status(SettlementStatus.COMPLETED).finalizedAt(departure.plusSeconds(60)).build());
        var item = settlementItems.saveAndFlush(SettlementItem.builder().settlementId(settlement.getId()).memberId(me.getId())
                .estimatedShareWon(5000).prepayDueWon(6000).status(SettlementItemStatus.ESTIMATED).build());
        Long accountId = accounts.findByRoomIdAndOwnerMemberIdAndPurpose(roomId, rep.getId(), AccountPurpose.PREPAY_RECEIVE).orElseThrow().getId();
        var transfer = transfers.saveAndFlush(Transfer.builder().settlementItemId(item.getId()).kind(TransferKind.PREPAY)
                .payerMemberId(me.getId()).payeeMemberId(rep.getId()).receiverAccountId(accountId).amountWon(6000)
                .status(TransferStatus.SENT).sentAt(departure).build());
        chat.synchronizeRoom(party.getId(), departure.plusSeconds(3599));
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.OPEN);
        chat.synchronizeRoom(party.getId(), departure.plusSeconds(3600));
        assertThat(accounts.findByRoomIdAndOwnerMemberIdAndPurpose(roomId, rep.getId(), AccountPurpose.PREPAY_RECEIVE)).isEmpty();
        var preserved = transfers.findById(transfer.getId()).orElseThrow();
        assertThat(preserved.getReceiverAccountId()).isNull();
        assertThat(preserved.getAmountWon()).isEqualTo(6000);
        assertThat(preserved.getStatus()).isEqualTo(TransferStatus.SENT);
        assertError(() -> accountService.getRecipientAccount(me.getId(), transfer.getId()), ErrorCode.ACCOUNT_EXPIRED);
        assertThat(messages.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(2);
        assertError(() -> chat.requireAccountAccess(roomId, rep.getId(), departure.plusSeconds(3600)), ErrorCode.ACCOUNT_EXPIRED);
        assertError(() -> accountService.register(roomId, AccountPurpose.PREPAY_RECEIVE, rep.getId(),
                new AccountInput("004", "12345678901", "이서준")), ErrorCode.ROOM_CLOSED);
    }

    @Test void disbandHooksCloseRoomAndDenyHistoryAndRetryImmediately() throws Exception {
        String key = key(), client = key();
        chat.send(student.getId(), roomId, new MessageCreate(client, "도착"), key, Instant.now());
        partyService.leaveParty(student.getId(), party.getId(), Instant.now(), null);
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.CLOSED);
        acceptRep();
        mvc.perform(get(messagePath()).with(authentication(auth(representative)))).andExpect(status().isGone());
        postMessage(representative, "도착", key(), key()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_CLOSED"));
    }

    @Test void missingRoomIsNotReadyAndSwaggerContainsAllOperations() throws Exception {
        jdbc.update("delete from chat_messages where room_id=?", roomId);
        rooms.deleteById(roomId);
        mvc.perform(get(roomPath()).with(authentication(auth(student)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_READY"));
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var doc = mapper.readTree(result.getResponse().getContentAsString()).path("paths");
        assertThat(doc.path("/api/v1/parties/{partyId}/room").path("get").path("operationId").asText()).isEqualTo("getPartyRoom");
        assertThat(doc.path("/api/v1/rooms/{roomId}/messages").path("get").path("operationId").asText()).isEqualTo("listMessages");
        assertThat(doc.path("/api/v1/rooms/{roomId}/messages").path("post").path("operationId").asText()).isEqualTo("sendMessage");
    }

    private void assertError(Runnable work, ErrorCode code) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    private String key() { return UUID.randomUUID().toString(); }
    private String roomPath() { return "/api/v1/parties/" + party.getId() + "/room"; }
    private String messagePath() { return "/api/v1/rooms/" + roomId + "/messages"; }
    private String body(String text, String client) throws Exception { return mapper.writeValueAsString(Map.of("clientMessageId", client, "text", text)); }
    private ResultActions postMessage(User user, String text, String client, String key) throws Exception {
        return mvc.perform(post(messagePath()).with(authentication(auth(user))).with(csrf().asHeader())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(text, client)));
    }
    private MessageResponse send(String text) { return chat.send(student.getId(), roomId, new MessageCreate(key(), text), key(), Instant.now()); }
    private UsernamePasswordAuthenticationToken auth(User u) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(u.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
    }
    private void acceptRep() { rep.confirm(Instant.now()); members.saveAndFlush(rep); }
    private void registerRepAccount() {
        accounts.saveAndFlush(RoomAccount.builder().roomId(roomId).ownerMemberId(rep.getId()).purpose(AccountPurpose.PREPAY_RECEIVE)
                .bankCode("004").accountNumberEncrypted(cryptor.encrypt("12345678901")).holderNameEncrypted(cryptor.encrypt("이서준")).build());
    }
    private User user(Long university, Role role, String name) {
        var u = users.saveAndFlush(User.builder().universityId(university).email("chat-test-" + key() + "@hufs.ac.kr").role(role).status(UserStatus.ACTIVE).build());
        profiles.saveAndFlush(StudentProfile.builder().userId(u.getId()).gender(Gender.FEMALE).realNameEncrypted(cryptor.encrypt(name))
                .sameGenderDefault(true).defaultDongId(dong.getId()).dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1")).build());
        return u;
    }
    private AdministrativeArea area(String name, AreaLevel level, Long parent) {
        return areas.saveAndFlush(AdministrativeArea.builder().officialCode("CT" + key().substring(0, 8)).name(name).level(level).parentId(parent).build());
    }
    private PartyMember member(User user, MemberDecision decision) {
        var journey = journeys.saveAndFlush(Journey.builder().clientJourneyKey(UUID.randomUUID()).userId(user.getId()).universityId(user.getUniversityId())
                .pickupPointId(pickup.getId()).destinationDongId(dong.getId()).intendedDepartureAt(departure).outcome(JourneyOutcome.UNRESOLVED).build());
        journeyIds.add(journey.getId());
        var ride = rides.saveAndFlush(RideRequest.builder().clientRequestKey(UUID.randomUUID()).journeyId(journey.getId()).universityId(user.getUniversityId())
                .pickupPointId(pickup.getId()).districtId(city.getId()).destinationDongId(dong.getId()).departureAt(departure)
                .dropoffLatEncrypted(cryptor.encrypt("37.3")).dropoffLngEncrypted(cryptor.encrypt("127.1"))
                .sameGenderOnly(true).genderSnapshot(Gender.FEMALE).status(RideRequestStatus.MATCHED).build());
        rideIds.add(ride.getId());
        return members.saveAndFlush(PartyMember.builder().partyId(party.getId()).requestId(ride.getId()).decision(decision).boardingStatus(BoardingStatus.UNKNOWN).build());
    }
}
