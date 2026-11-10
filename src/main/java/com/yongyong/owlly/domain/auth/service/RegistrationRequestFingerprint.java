package com.yongyong.owlly.domain.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.auth.dto.StudentRegistrationRequest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRequestFingerprint {
    private final ObjectMapper mapper;
    private final String secret;

    public RegistrationRequestFingerprint(ObjectMapper mapper, @Value("${owlly.verification.secret:}") String secret) {
        this.mapper = mapper;
        this.secret = secret;
    }

    public String hash(StudentRegistrationRequest request) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(mapper.writeValueAsBytes(request)));
        } catch (java.security.GeneralSecurityException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("가입 요청 식별값 생성 실패");
        }
    }
}
