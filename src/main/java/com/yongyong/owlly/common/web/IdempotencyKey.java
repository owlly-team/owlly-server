package com.yongyong.owlly.common.web;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import java.util.List;
import java.util.UUID;

/**
 * Idempotency-Key 헤더 검사 (docs/api.md 1절 "멱등성과 동시 수정").
 *
 * <p>값이 UUID 형식인지만 확인한다. 같은 요청을 다시 보내도 결과가 같은 명령(예: 이미 확정했으면 같은 응답)은
 * 결과를 따로 저장하지 않아도 되므로, 멱등 요청 저장소 없이 형식만 검사한다(로그아웃 API 와 같은 방식).
 */
public final class IdempotencyKey {

    private IdempotencyKey() {
    }

    /** UUID 형식이 아니면 422 VALIDATION_ERROR. */
    public static void requireUuid(String key) {
        try {
            if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR, List.of("Idempotency-Key: UUID 형식이어야 합니다."));
        }
    }
}
