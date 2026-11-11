package com.yongyong.owlly.domain.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.*;
import com.yongyong.owlly.domain.auth.dto.StaffLoginRequest;
import com.yongyong.owlly.domain.auth.entity.StaffLoginAttempt;
import com.yongyong.owlly.domain.auth.entity.StaffLoginReceipt;
import com.yongyong.owlly.domain.auth.repository.StaffLoginAttemptRepository;
import com.yongyong.owlly.domain.auth.repository.StaffLoginReceiptRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffLoginService {
    private final UserRepository users;
    private final UniversityRepository universities;
    private final StaffLoginAttemptRepository attempts;
    private final StaffLoginReceiptRepository receipts;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;
    private final String dummyHash;
    @Value("${owlly.verification.secret:}") private String secret;
    @Value("${owlly.staff-login.max-failures:5}") private int maxFailures;
    @Value("${owlly.staff-login.lock-seconds:1800}") private long lockSeconds;
    @Value("${owlly.staff-login.receipt-ttl-seconds:1800}") private long receiptTtlSeconds;

    public StaffLoginService(UserRepository users, UniversityRepository universities,
            StaffLoginAttemptRepository attempts, StaffLoginReceiptRepository receipts, JdbcTemplate jdbc,
            PasswordEncoder passwords, ObjectMapper mapper) {
        this.users = users;
        this.universities = universities;
        this.attempts = attempts;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.mapper = mapper;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** 결과를 반환해 실패 횟수·멱등 기록도 커밋한다. 오류 응답은 컨트롤러가 커밋 후 발생시킨다. */
    @Transactional
    public Result login(StaffLoginRequest request, String key) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR, List.of("password: UTF-8 72바이트 이하여야 합니다."));
        }
        if (maxFailures < 1 || lockSeconds < 1 || receiptTtlSeconds < 1) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        String requestHash;
        try { requestHash = hmac("staff-request:" + mapper.writeValueAsString(request)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("관계자 로그인 요청 식별값 생성 실패"); }
        String accountKey = hmac("staff-account:" + request.email());
        lock("staff-key:" + key);
        // 키 단위 advisory lock은 로그인끼리, 행 잠금은 정리 작업과의 동시 실행을 보호한다.
        var storedReceipt = receipts.findByRequestKeyForUpdate(key);
        Instant lookupTime = Instant.now();
        StaffLoginReceipt prior = storedReceipt.filter(r -> lookupTime.isBefore(r.getExpiresAt())).orElse(null);
        if (prior != null && !MessageDigest.isEqual(requestHash.getBytes(StandardCharsets.UTF_8), prior.getRequestHash().getBytes(StandardCharsets.UTF_8))) {
            throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        lock("staff-account:" + accountKey);
        // 잠금을 기다린 뒤의 시각으로 만료를 검사한다.
        Instant now = Instant.now();
        if (prior != null && !now.isBefore(prior.getExpiresAt())) prior = null;
        Instant receiptExpiry = prior == null ? now.plusSeconds(receiptTtlSeconds) : prior.getExpiresAt();
        StaffLoginAttempt attempt = attempts.findByAccountKeyForUpdate(accountKey).orElseGet(() -> new StaffLoginAttempt(accountKey, Instant.now()));
        attempt.visit(now);
        attempt = attempts.save(attempt);
        if (attempt.getBlockedUntil() != null) {
            if (prior == null) saveReceipt(key, requestHash, null, ErrorCode.RATE_LIMITED, receiptExpiry);
            return Result.limited(attempt.getBlockedUntil(), now);
        }
        // 실패한 같은 요청을 재전송해도 횟수를 더하지 않는다. 잠금 종료 후 RATE_LIMITED 결과는 새로 인증한다.
        if (prior != null && prior.getErrorCode() == ErrorCode.INVALID_CREDENTIALS) return Result.failure(ErrorCode.INVALID_CREDENTIALS);
        User user = users.findByEmail(request.email()).orElse(null);
        if (!matchesPassword(user, request.password())) {
            attempt.fail(now, maxFailures, lockSeconds);
            ErrorCode error = attempt.getBlockedUntil() == null ? ErrorCode.INVALID_CREDENTIALS : ErrorCode.RATE_LIMITED;
            saveReceipt(key, requestHash, null, error, receiptExpiry);
            return error == ErrorCode.RATE_LIMITED ? Result.limited(attempt.getBlockedUntil(), now) : Result.failure(error);
        }
        ErrorCode unavailable = null;
        if (user.getStatus() == UserStatus.WITHDRAWN || !universities.existsById(user.getUniversityId())) unavailable = ErrorCode.ACCOUNT_UNAVAILABLE;
        else if (user.getStatus() == UserStatus.SUSPENDED) unavailable = ErrorCode.ACCOUNT_SUSPENDED;
        else if (prior != null && prior.getUserId() != null
                && (!prior.getUserId().equals(user.getId()) || !prior.getUniversityId().equals(user.getUniversityId()))) unavailable = ErrorCode.ACCOUNT_UNAVAILABLE;
        if (unavailable != null) {
            // 성공 기록의 계정·대학 바인딩은 일시 정지/변경 때문에 덮어쓰지 않는다.
            if (prior == null || prior.getErrorCode() != null) saveReceipt(key, requestHash, null, unavailable, receiptExpiry);
            return Result.failure(unavailable);
        }
        attempt.reset();
        saveReceipt(key, requestHash, user, null, receiptExpiry);
        return new Result(user, null, 0);
    }

    private boolean matchesPassword(User user, String password) {
        String hash = user == null ? null : user.getPasswordHash();
        boolean staffHash = user != null && user.getRole() == Role.STAFF && hash != null
                && hash.matches("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
        if (!staffHash) { passwords.matches(password, dummyHash); return false; }
        return passwords.matches(password, hash);
    }

    private void saveReceipt(String key, String requestHash, User user, ErrorCode error, Instant expiresAt) {
        receipts.saveAndFlush(new StaffLoginReceipt(key, requestHash, user == null ? null : user.getId(),
                user == null ? null : user.getUniversityId(), error, expiresAt));
    }

    private void lock(String value) { jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", value); }

    private String hmac(String value) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("관계자 로그인 식별값 생성 실패"); }
    }

    public record Result(User user, ErrorCode error, long retryAfterSeconds) {
        static Result failure(ErrorCode error) { return new Result(null, error, 0); }
        static Result limited(Instant until, Instant now) {
            return new Result(null, ErrorCode.RATE_LIMITED, Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000));
        }
        public User requireUser() {
            if (error == ErrorCode.RATE_LIMITED) throw new RateLimitException(retryAfterSeconds);
            if (error != null) throw new OwllyException(error);
            return user;
        }
    }
}
