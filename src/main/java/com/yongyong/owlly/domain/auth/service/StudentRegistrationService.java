package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.auth.dto.StudentRegistrationRequest;
import com.yongyong.owlly.domain.auth.entity.StudentRegistrationReceipt;
import com.yongyong.owlly.domain.auth.repository.StudentRegistrationReceiptRepository;
import com.yongyong.owlly.domain.university.SupportedUniversities;
import com.yongyong.owlly.domain.university.repository.UniversityDomainRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import com.yongyong.owlly.domain.verification.service.EmailVerificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentRegistrationService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final UniversityRepository universities;
    private final UniversityDomainRepository universityDomains;
    private final EmailVerificationService verifications;
    private final ProfileOptionsService options;
    private final RegistrationDropoffLocation locations;
    private final TextCryptor cryptor;
    private final StudentRegistrationReceiptRepository receipts;
    private final RegistrationRequestLock locks;
    private final RegistrationRequestFingerprint fingerprints;

    /** 증표 소비·계정·프로필·멱등 기록을 함께 커밋한다. 세션 발급은 호출한 컨트롤러에서 수행한다. */
    @Transactional
    public User register(StudentRegistrationRequest request, String key) {
        var profile = request.profile();
        Gender gender = options.parseGender(profile.genderCode());
        String hash = fingerprints.hash(request);
        locks.lock(key);
        var prior = receipts.findById(key);
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), receipt.getRequestHash().getBytes(StandardCharsets.UTF_8))) {
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (!Instant.now().isBefore(receipt.getExpiresAt())) throw new OwllyException(ErrorCode.VERIFICATION_EXPIRED);
            User user = users.findById(receipt.getUserId()).orElseThrow(() -> new OwllyException(ErrorCode.ACCOUNT_UNAVAILABLE));
            if (user.getStatus() != UserStatus.ACTIVE || user.getRole() != Role.STUDENT) {
                throw new OwllyException(user.getStatus() == UserStatus.SUSPENDED
                        ? ErrorCode.ACCOUNT_SUSPENDED : ErrorCode.ACCOUNT_UNAVAILABLE);
            }
            validateUniversity(user.getUniversityId(), user.getEmail());
            return user;
        }
        // consumeGrant 내부의 이메일 단위 잠금으로 서로 다른 키의 동시 가입도 직렬화된다.
        var verified = verifications.consumeGrant(request.verificationToken());
        if (users.existsByEmail(verified.email())) throw new OwllyException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        validateUniversity(verified.universityId(), verified.email());
        var point = profile.defaultDropoff();
        var dong = locations.resolveDong(point.latitude(), point.longitude());
        User user = users.saveAndFlush(User.builder()
                .universityId(verified.universityId()).email(verified.email())
                .role(Role.STUDENT).status(UserStatus.ACTIVE).emailVerifiedAt(verified.verifiedAt()).build());
        profiles.saveAndFlush(StudentProfile.builder().userId(user.getId())
                .realNameEncrypted(cryptor.encrypt(profile.realName())).gender(gender).sameGenderDefault(profile.sameGenderDefault())
                .defaultDongId(dong.getId())
                .dropoffLatEncrypted(cryptor.encrypt(Double.toString(point.latitude())))
                .dropoffLngEncrypted(cryptor.encrypt(Double.toString(point.longitude()))).build());
        receipts.saveAndFlush(new StudentRegistrationReceipt(key, hash, user.getId(), verified.grantExpiresAt()));
        return user;
    }

    private void validateUniversity(Long universityId, String email) {
        var university = universities.findById(universityId)
                .filter(u -> u.isActive() && SupportedUniversities.contains(u.getName()))
                .orElseThrow(() -> new OwllyException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED));
        String domain = email.substring(email.lastIndexOf('@') + 1);
        if (!domain.equals(SupportedUniversities.EMAIL_DOMAINS.get(university.getName()))
                || !universityDomains.existsByUniversityIdAndDomainAndActiveTrue(universityId, domain)) {
            throw new OwllyException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
    }
}
