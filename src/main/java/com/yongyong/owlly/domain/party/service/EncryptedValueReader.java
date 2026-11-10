package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 팟 쪽에서 암호화된 값(하차 좌표·실명)을 읽는다.
 *
 * <p>매칭 작업(팟 만들기), 팟 조회(이름 일부), 팟 이탈(경로 다시 계산)이 같은 방식으로 읽어야 해서 한곳에 모았다.
 * 시드·테스트 데이터의 평문 값("TEST:37.2510", "TEST:김민준")은 그대로 읽고, 그 외는 공통 TextCryptor 로 복호화한다.
 * 동승 신청·가입 API 가 생기면 실제 값은 모두 암호화되어 저장된다.
 */
@Component
@RequiredArgsConstructor
public class EncryptedValueReader {

    static final String SEED_PLAINTEXT_PREFIX = "TEST:";

    private final TextCryptor textCryptor;

    /** 저장된 문자열 한 값. */
    public String readText(String stored) {
        if (stored.startsWith(SEED_PLAINTEXT_PREFIX)) {
            return stored.substring(SEED_PLAINTEXT_PREFIX.length()).trim();
        }
        return textCryptor.decrypt(stored);
    }

    /** 신청의 하차 좌표. 읽을 수 없으면 어느 신청인지 알 수 있게 예외를 던진다. */
    public GeoPoint readDropoff(RideRequest request) {
        try {
            return new GeoPoint(Double.parseDouble(readText(request.getDropoffLatEncrypted())),
                    Double.parseDouble(readText(request.getDropoffLngEncrypted())));
        } catch (RuntimeException e) {
            throw new IllegalStateException("하차 좌표를 읽을 수 없습니다. requestId=" + request.getId(), e);
        }
    }
}
