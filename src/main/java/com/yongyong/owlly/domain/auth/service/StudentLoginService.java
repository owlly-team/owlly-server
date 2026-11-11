package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.auth.dto.StudentLoginRequest;
import com.yongyong.owlly.domain.auth.entity.StudentLoginReceipt;
import com.yongyong.owlly.domain.auth.repository.StudentLoginReceiptRepository;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.entity.UserStatus;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import com.yongyong.owlly.domain.verification.service.EmailVerificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentLoginService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final EmailVerificationService verifications;
    private final StudentLoginReceiptRepository receipts;
    private final JdbcTemplate jdbc;
    @Value("${owlly.verification.secret:}") private String secret;

    /** 증표 소비와 재시도 기록은 원자적으로 처리한다. 계정·프로필은 생성/수정하지 않는다. */
    @Transactional
    public User login(StudentLoginRequest request, String key) {
        String hash = fingerprint(request.verificationToken());
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "student-login:" + key);
        var prior = receipts.findById(key);
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                    receipt.getRequestHash().getBytes(StandardCharsets.UTF_8))) {
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (!Instant.now().isBefore(receipt.getExpiresAt())) {
                throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
            }
            User user = users.findById(receipt.getUserId())
                    .orElseThrow(() -> new OwllyException(ErrorCode.ACCOUNT_UNAVAILABLE));
            validateAccount(user, receipt.getUniversityId());
            return user;
        }
        // 이메일 단위 잠금은 consumeGrant가 담당하므로 가입과 로그인 사이의 동시 소비도 막는다.
        var verified = verifications.consumeGrant(request.verificationToken());
        User user = users.findByEmail(verified.email())
                .orElseThrow(() -> new OwllyException(ErrorCode.ACCOUNT_NOT_FOUND));
        validateAccount(user, verified.universityId());
        receipts.saveAndFlush(new StudentLoginReceipt(key, hash, user.getId(), verified.universityId(), verified.grantExpiresAt()));
        return user;
    }

    private void validateAccount(User user, Long universityId) {
        if (user.getRole() != Role.STUDENT || user.getStatus() == UserStatus.WITHDRAWN
                || !user.getUniversityId().equals(universityId) || !profiles.existsById(user.getId())) {
            throw new OwllyException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) throw new OwllyException(ErrorCode.ACCOUNT_SUSPENDED);
    }

    private String fingerprint(String token) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("student-login:" + token).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("로그인 요청 식별값 생성 실패");
        }
    }
}
