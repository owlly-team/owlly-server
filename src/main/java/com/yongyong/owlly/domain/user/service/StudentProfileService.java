package com.yongyong.owlly.domain.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.domain.auth.dto.SessionResponse.CurrentUser;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.user.dto.StudentProfilePatch;
import com.yongyong.owlly.domain.user.dto.StudentProfileResponse;
import com.yongyong.owlly.domain.user.entity.*;
import com.yongyong.owlly.domain.user.repository.*;
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
public class StudentProfileService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final UniversityRepository universities;
    private final AdministrativeAreaRepository areas;
    private final DropoffLocationService locations;
    private final TextCryptor cryptor;
    private final StudentProfileUpdateReceiptRepository receipts;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    @Value("${owlly.verification.secret:}") private String secret;
    @Value("${owlly.profile.receipt-ttl-seconds:86400}") private long receiptTtlSeconds;

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new OwllyException(ErrorCode.UNAUTHENTICATED));
        validateAccount(user);
        return describe(user, profiles.findById(userId).orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional
    public StudentProfileResponse updateProfile(Long userId, StudentProfilePatch input, String key, String ifMatch) {
        if (!input.hasChanges()) throw new OwllyException(ErrorCode.VALIDATION_ERROR);
        if (receiptTtlSeconds < 1) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        String hash = fingerprint(userId, input);
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "profile-update:" + key);
        var stored = receipts.findByKeyForUpdate(key);
        Instant lookupTime = Instant.now();
        var prior = stored.filter(r -> lookupTime.isBefore(r.getExpiresAt())).orElse(null);
        if (prior != null && (!prior.getUserId().equals(userId)
                || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), prior.getRequestHash().getBytes(StandardCharsets.UTF_8)))) {
            throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        // 지역 판별 → 사용자 잠금 순서는 귀가·동승 생성과 같다. 완료한 재시도는 외부 API를 다시 호출하지 않는다.
        AdministrativeArea dong = null;
        if (prior == null && input.getDefaultDropoff() != null) {
            var point = input.getDefaultDropoff();
            dong = locations.resolveDong(point.latitude(), point.longitude());
            if (dong == null || dong.getId() == null || dong.getLevel() != AreaLevel.DONG) {
                throw new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE);
            }
        }
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new OwllyException(ErrorCode.UNAUTHENTICATED));
        validateAccount(user);
        StudentProfile profile = profiles.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        StudentProfileResponse current = describe(user, profile);
        // 이미 성공한 명령은 ETag보다 먼저 확인한다. 이후의 수정 결과를 과거 요청으로 되돌리지 않는다.
        if (prior != null) return current;
        if (!ETags.matches(ifMatch, current)) throw new OwllyException(ErrorCode.PRECONDITION_FAILED);

        if (input.getSameGenderDefault() != null) profile.changeSameGenderDefault(input.getSameGenderDefault());
        if (dong != null) {
            var point = input.getDefaultDropoff();
            profile.changeDefaultDropoff(dong.getId(), cryptor.encrypt(Double.toString(point.latitude())),
                    cryptor.encrypt(Double.toString(point.longitude())));
        }
        profiles.flush();
        receipts.saveAndFlush(new StudentProfileUpdateReceipt(key, userId, hash, Instant.now().plusSeconds(receiptTtlSeconds)));
        return describe(user, profile);
    }

    private void validateAccount(User user) {
        if (user.getStatus() == UserStatus.WITHDRAWN) throw new OwllyException(ErrorCode.UNAUTHENTICATED);
        if (user.getStatus() == UserStatus.SUSPENDED) throw new OwllyException(ErrorCode.ACCOUNT_SUSPENDED);
        if (user.getRole() != Role.STUDENT) throw new OwllyException(ErrorCode.FORBIDDEN);
    }

    private StudentProfileResponse describe(User user, StudentProfile profile) {
        var university = universities.findById(user.getUniversityId()).orElseThrow(() -> new OwllyException(ErrorCode.ACCOUNT_UNAVAILABLE));
        var dong = areas.findById(profile.getDefaultDongId()).filter(a -> a.getLevel() == AreaLevel.DONG)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        return new StudentProfileResponse(CurrentUser.from(user, university.getName()), cryptor.decrypt(profile.getRealNameEncrypted()),
                profile.getGender().name(), profile.isSameGenderDefault(), dong.getId(), dong.getName(),
                new StudentProfileResponse.Point(Double.parseDouble(cryptor.decrypt(profile.getDropoffLatEncrypted())),
                        Double.parseDouble(cryptor.decrypt(profile.getDropoffLngEncrypted()))));
    }

    private String fingerprint(Long userId, StudentProfilePatch input) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("profile-patch:" + userId + ":" + mapper.writeValueAsString(input))
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("프로필 수정 요청 식별값 생성 실패");
        }
    }
}
