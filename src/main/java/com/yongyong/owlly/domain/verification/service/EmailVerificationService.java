package com.yongyong.owlly.domain.verification.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.university.repository.UniversityDomainRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.university.SupportedUniversities;
import com.yongyong.owlly.domain.verification.dto.*;
import com.yongyong.owlly.domain.verification.entity.EmailVerification;
import com.yongyong.owlly.domain.verification.entity.DeliveryStatus;
import com.yongyong.owlly.domain.verification.entity.VerificationAttemptLimit;
import com.yongyong.owlly.domain.verification.entity.VerificationFailedRequest;
import com.yongyong.owlly.domain.verification.repository.EmailVerificationRepository;
import com.yongyong.owlly.domain.verification.repository.VerificationAttemptLimitRepository;
import com.yongyong.owlly.domain.verification.repository.VerificationFailedRequestRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final EmailVerificationRepository verifications;
    private final VerificationAttemptLimitRepository attemptLimits;
    private final VerificationFailedRequestRepository failedRequests;
    private final UniversityRepository universities;
    private final UniversityDomainRepository domains;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final PlatformTransactionManager transactionManager;
    @Value("${owlly.verification.secret:}") private String secret;
    @Value("${owlly.verification.mail-from:}") private String mailFrom;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailVerificationResponse issue(EmailVerificationRequest request, String key) {
        checkSecret();
        PreparedIssue prepared = newTransaction().execute(status -> prepareIssue(request, key));
        if (prepared.code() == null) return prepared.response();

        DeliveryResult result = newTransaction().execute(status -> deliver(prepared, key));
        if (!result.sent()) throw new OwllyException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        return result.response();
    }

    private PreparedIssue prepareIssue(EmailVerificationRequest request, String key) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        lock("issue-key:" + key);
        lock("email:" + email);
        var prior = verifications.findByIssueKey(key);
        if (prior.isPresent()) {
            var v = prior.get();
            if (!v.getEmail().equals(email) || !v.getUniversityId().equals(request.universityId()))
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            if (v.getInvalidatedAt() != null || v.getConsumedAt() != null || !Instant.now().isBefore(v.getExpiresAt()))
                throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
            if (v.getDeliveryStatus() != null && v.getDeliveryStatus() != DeliveryStatus.SENT)
                throw new OwllyException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
            return new PreparedIssue(v.getId(), email, null, response(v));
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || !universities.findById(request.universityId())
                .filter(u -> u.isActive() && SupportedUniversities.contains(u.getName())
                        && email.substring(at + 1).equals(SupportedUniversities.EMAIL_DOMAINS.get(u.getName()))).isPresent()
                || !domains.existsByUniversityIdAndDomainAndActiveTrue(request.universityId(), email.substring(at + 1)))
            throw new OwllyException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);

        Instant now = Instant.now();
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        Instant start = today.atStartOfDay(SEOUL).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(SEOUL).toInstant();
        if (verifications.countByEmailAndSentAtGreaterThanEqualAndSentAtLessThan(email, start, end) >= 10)
            throw new VerificationRateLimitException(Duration.between(now, end).toSeconds() + 1);
        var latest = verifications.findFirstByEmailOrderBySentAtDescIdDesc(email);
        if (latest.isPresent() && now.isBefore(latest.get().getSentAt().plusSeconds(60)))
            throw new VerificationRateLimitException(Duration.between(now, latest.get().getSentAt().plusSeconds(60)).toSeconds() + 1);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Email delivery unavailable: mail sender is not configured");
            throw new OwllyException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
        if (mailFrom.isBlank()) {
            log.warn("Email delivery unavailable: MAIL_FROM is not configured");
            throw new OwllyException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        verifications.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(email).forEach(v -> v.invalidate(now));
        EmailVerification v = verifications.save(EmailVerification.builder().universityId(request.universityId())
                .email(email).codeHash(mac("code:" + email + ":" + code)).sentAt(now)
                .expiresAt(now.plusSeconds(600)).issueKey(key).deliveryStatus(DeliveryStatus.PENDING).build());
        return new PreparedIssue(v.getId(), email, code, response(v));
    }

    private DeliveryResult deliver(PreparedIssue prepared, String key) {
        lock("issue-key:" + key);
        lock("email:" + prepared.email());
        EmailVerification v = verifications.findForUpdate(prepared.id())
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (v.getInvalidatedAt() != null || v.getConsumedAt() != null || !Instant.now().isBefore(v.getExpiresAt()))
            throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(prepared.email());
        message.setSubject("[Owlly] 학교 이메일 인증번호");
        message.setText("인증번호는 " + prepared.code() + "입니다. 발급 후 10분 동안 유효합니다.");
        try {
            mailSender.getObject().send(message);
            v.markDelivery(DeliveryStatus.SENT);
        }
        catch (MailException ex) {
            log.warn("Email delivery failed: {}", ex.getClass().getSimpleName());
            v.markDelivery(DeliveryStatus.UNKNOWN);
            return new DeliveryResult(false, null);
        }
        return new DeliveryResult(true, response(v));
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private record PreparedIssue(Long id, String email, String code, EmailVerificationResponse response) {}
    private record DeliveryResult(boolean sent, EmailVerificationResponse response) {}

    @Transactional(noRollbackFor = OwllyException.class)
    public VerificationGrantResponse verify(Long id, String code, String key) {
        checkSecret();
        lock("verify-key:" + key);
        String requestHash = mac("verify-request:" + id + ":" + code);
        var failed = failedRequests.findById(key);
        if (failed.isPresent()) {
            VerificationFailedRequest previous = failed.get();
            if (!MessageDigest.isEqual(previous.getRequestHash().getBytes(StandardCharsets.UTF_8),
                    requestHash.getBytes(StandardCharsets.UTF_8)))
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            Instant now = Instant.now();
            if (previous.getBlockedUntil() != null && now.isBefore(previous.getBlockedUntil()))
                throw new VerificationRateLimitException(Duration.between(now, previous.getBlockedUntil()).toSeconds() + 1);
            if (!now.isBefore(previous.getExpiresAt())) throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
            throw new OwllyException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        var prior = verifications.findByVerifyKey(key);
        if (prior.isPresent() && !prior.get().getId().equals(id)) throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
        String email = verifications.findEmailById(id).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        lock("email:" + email);
        EmailVerification v = verifications.findForUpdate(id).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean codeMatches = MessageDigest.isEqual(v.getCodeHash().getBytes(StandardCharsets.UTF_8),
                mac("code:" + v.getEmail() + ":" + code).getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        if (prior.isPresent()) {
            if (!codeMatches) throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            if (v.getGrantConsumedAt() != null) throw new OwllyException(ErrorCode.VERIFICATION_USED);
            if (!now.isBefore(v.getGrantExpiresAt())) throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
            return new VerificationGrantResponse(token(v, key), v.getGrantExpiresAt());
        }
        if (v.getConsumedAt() != null || v.getInvalidatedAt() != null) throw new OwllyException(ErrorCode.VERIFICATION_USED);
        if (!now.isBefore(v.getExpiresAt())) throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
        VerificationAttemptLimit limit = attemptLimits.findById(email).orElseGet(() -> new VerificationAttemptLimit(email));
        limit.resetExpired(now);
        if (limit.getBlockedUntil() != null) {
            throw new VerificationRateLimitException(Duration.between(now, limit.getBlockedUntil()).toSeconds() + 1);
        }
        if (!codeMatches) {
            v.fail();
            limit.fail(now);
            attemptLimits.save(limit);
            failedRequests.save(new VerificationFailedRequest(key, requestHash, v.getExpiresAt(), limit.getBlockedUntil()));
            if (limit.getBlockedUntil() != null) throw new VerificationRateLimitException(600);
            throw new OwllyException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        limit.reset();
        attemptLimits.save(limit);
        String token = token(v, key);
        Instant expires = now.plusSeconds(600);
        v.verify(key, mac("grant:" + token), now, expires);
        return new VerificationGrantResponse(token, expires);
    }

    /** 가입 트랜잭션에서 호출: 계정 생성이 실패하면 증표 소비도 롤백된다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public VerifiedEmail consumeGrant(String token) {
        checkSecret();
        Long id = verifications.findIdByGrantHash(mac("grant:" + token))
                .orElseThrow(() -> new OwllyException(ErrorCode.VERIFICATION_USED));
        String email = verifications.findEmailById(id).orElseThrow(() -> new OwllyException(ErrorCode.VERIFICATION_USED));
        lock("email:" + email);
        EmailVerification v = verifications.findForUpdate(id).orElseThrow(() -> new OwllyException(ErrorCode.VERIFICATION_USED));
        if (v.getGrantConsumedAt() != null) throw new OwllyException(ErrorCode.VERIFICATION_USED);
        Instant now = Instant.now();
        if (v.getGrantExpiresAt() == null || !now.isBefore(v.getGrantExpiresAt()))
            throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
        v.consumeGrant(now);
        return new VerifiedEmail(v.getUniversityId(), v.getEmail(), v.getConsumedAt(), v.getGrantExpiresAt());
    }

    public record VerifiedEmail(Long universityId, String email, Instant verifiedAt, Instant grantExpiresAt) {}

    private String token(EmailVerification v, String key) { return mac("token:" + v.getId() + ":" + key); }
    private EmailVerificationResponse response(EmailVerification v) {
        Instant available = v.getSentAt().plusSeconds(60);
        LocalDate day = v.getSentAt().atZone(SEOUL).toLocalDate();
        Instant start = day.atStartOfDay(SEOUL).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(SEOUL).toInstant();
        if (verifications.countByEmailAndSentAtGreaterThanEqualAndSentAtLessThan(v.getEmail(), start, end) >= 10
                && available.isBefore(end)) available = end;
        return new EmailVerificationResponse(v.getId(), v.getExpiresAt(), available);
    }
    private void lock(String value) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", value);
    }
    private void checkSecret() {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
    }
    private String mac(String value) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
