package com.yongyong.owlly.common.crypto;

/**
 * 저장 시 암호화가 필요한 민감 문자열(계좌번호·예금주·좌표 등)을 다루는 규격.
 *
 * <p>같은 열쇠로 잠그고 푸는 대칭키 방식. 나중에 돌려줘야 하는 값(계좌번호 등)에 쓴다.
 * (비밀번호처럼 되돌릴 필요가 없는 값은 이게 아니라 해시를 쓴다.)
 *
 * <p>서비스는 이 규격만 알고, 실제 알고리즘·키 관리는 구현체가 담당한다.
 * 그래서 나중에 KMS/Vault 기반 구현으로 갈아끼워도 서비스 코드는 그대로다.
 */
public interface TextCryptor {

    /** 평문 → 저장용 암호문(문자열). */
    String encrypt(String plaintext);

    /** 저장된 암호문 → 평문. */
    String decrypt(String ciphertext);
}
