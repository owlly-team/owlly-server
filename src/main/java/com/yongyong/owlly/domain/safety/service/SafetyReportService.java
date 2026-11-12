package com.yongyong.owlly.domain.safety.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.*;
import com.yongyong.owlly.domain.chat.dto.MessageResponse;
import com.yongyong.owlly.domain.chat.entity.MessageType;
import com.yongyong.owlly.domain.chat.repository.*;
import com.yongyong.owlly.domain.chat.service.*;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.safety.dto.*;
import com.yongyong.owlly.domain.safety.entity.*;
import com.yongyong.owlly.domain.safety.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기획 질문 v3: 신고별 대화 사본 7일 보존, 같은 방의 동일 신고자→대상은 1회, 차단은 계속 유지. */
@Service
@RequiredArgsConstructor
@Transactional
public class SafetyReportService {
    private final SafetyReportRepository reports;
    private final SafetyReportReceiptRepository receipts;
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final PartyMemberRepository members;
    private final ChatRoomService chat;
    private final ChatTextMasker masker;
    private final TextCryptor cryptor;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    @Value("${owlly.verification.secret:}") private String secret;

    public SafetyReportResponse create(Long userId, Long roomId, ReportCreate input, String key) {
        Long targetId = parseId(input.targetMemberId());
        Long messageId = input.messageId() == null ? null : parseId(input.messageId());
        String hash = fingerprint(userId, roomId, input);
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "safety-report-key:" + key);
        // 메시지 전송·이탈·종료와 같은 팟 잠금. 사본은 이 잠금을 획득한 뒤 보이는 대화 전체다.
        Long reporterId = chat.requireReportAccess(userId, roomId);
        var prior = receipts.findById(key);
        if (prior.isPresent()) {
            if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), prior.get().getRequestHash().getBytes(StandardCharsets.UTF_8)))
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return SafetyReportResponse.of(reports.findById(prior.get().getReportId())
                    .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND)));
        }
        var room = rooms.findById(roomId).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        var target = members.findById(targetId)
                .filter(m -> m.getPartyId().equals(room.getPartyId()) && !m.getId().equals(reporterId))
                .orElseThrow(() -> new OwllyException(ErrorCode.INVALID_REPORT_TARGET));
        Long targetUserId = members.findUserIdByMemberId(target.getId())
                .filter(id -> !id.equals(userId)).orElseThrow(() -> new OwllyException(ErrorCode.INVALID_REPORT_TARGET));
        if (messageId != null) messages.findById(messageId)
                .filter(m -> m.getRoomId().equals(roomId) && targetId.equals(m.getSenderMemberId()) && m.getMessageType() == MessageType.USER)
                .orElseThrow(() -> new OwllyException(ErrorCode.INVALID_REPORT_TARGET));
        if (reports.existsByRoomIdAndReporterMemberIdAndTargetMemberId(roomId, reporterId, targetId))
            throw new OwllyException(ErrorCode.REPORT_ALREADY_EXISTS);

        // PostgreSQL 시각 정밀도에 맞춰 응답과 재시도의 만료 시각을 동일하게 보존한다.
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String details = input.details() == null ? null : masker.mask(input.details());
        var evidence = new Evidence(roomId, capturedAt, details, messages.findByRoomIdOrderByIdAsc(roomId).stream()
                .map(m -> new MessageResponse(m.getId(), m.getRoomId(), m.getSenderMemberId(), m.getMessageType(),
                        masker.mask(m.getBody()), m.getCreatedAt())).toList());
        SafetyReport report = reports.saveAndFlush(SafetyReport.builder().roomId(roomId).reporterMemberId(reporterId)
                .targetMemberId(targetId).messageId(messageId).reasonCode(ReportReasonCode.valueOf(input.reasonCode()))
                .evidenceEncrypted(cryptor.encrypt(json(evidence))).evidenceDeleteAfter(capturedAt.plus(7, ChronoUnit.DAYS))
                .status(SafetyReportStatus.RECEIVED).build());
        // 여러 방·양쪽의 신고가 동시에 접수되어도 차단 쌍은 하나. 기존 원인 신고를 덮어쓰지 않는다.
        jdbc.update("""
                insert into user_blocks(user_low_id,user_high_id,source_report_id,created_at,updated_at)
                values (?,?,?,current_timestamp,current_timestamp)
                on conflict (user_low_id,user_high_id) do nothing
                """, Math.min(userId, targetUserId), Math.max(userId, targetUserId), report.getId());
        receipts.saveAndFlush(new SafetyReportReceipt(key, hash, report.getId()));
        return SafetyReportResponse.of(report);
    }

    private Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) { throw new OwllyException(ErrorCode.VALIDATION_ERROR); }
    }

    private String fingerprint(Long userId, Long roomId, ReportCreate input) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(json(Arrays.asList("safety-report", userId, roomId,
                    input.targetMemberId(), input.reasonCode(), input.messageId(), input.details())).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("신고 요청 식별값 생성 실패"); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("신고 데이터 직렬화 실패"); }
    }

    private record Evidence(Long roomId, Instant capturedAt, String details, List<MessageResponse> messages) {}
}
