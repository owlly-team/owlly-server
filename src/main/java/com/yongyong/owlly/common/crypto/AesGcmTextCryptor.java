package com.yongyong.owlly.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 기반 문자열 암복호화 구현.
 *
 * <p>GCM 은 암호화와 변조 감지(인증 태그)를 함께 해 주는 표준 방식이다.
 * 매 암호화마다 임의의 IV(초기화 벡터) 12바이트를 새로 만들어 앞에 붙인다.
 * 그래서 같은 계좌번호라도 저장할 때마다 암호문이 달라져, 값 추측을 어렵게 한다.
 *
 * <p>저장 형태: Base64( IV(12B) + 암호문+인증태그 ).
 * 키는 코드에 박지 않고 설정(owlly.crypto.aes-key, Base64 32바이트)에서 주입한다.
 * TODO(운영): 키를 KMS/Vault 로 관리하고 주기적 교체(key rotation)를 적용한다.
 */
@Component
public class AesGcmTextCryptor implements TextCryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;       // GCM 권장 IV 길이(바이트)
    private static final int TAG_LENGTH_BIT = 128;  // 인증 태그 길이(비트)

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmTextCryptor(@Value("${owlly.crypto.aes-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("owlly.crypto.aes-key 는 Base64 로 16/24/32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv); // 매번 새 IV

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);                       // 앞 12바이트 = IV
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);               // 나머지 = 암호문+태그

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호화에 실패했습니다.", e);
        }
    }
}
