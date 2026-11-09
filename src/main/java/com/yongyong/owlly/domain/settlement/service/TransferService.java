package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.settlement.dto.TransferResponse;
import com.yongyong.owlly.domain.settlement.entity.Transfer;
import com.yongyong.owlly.domain.settlement.repository.TransferRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수동 송금 확인 (API-39 '보냈어요', API-40 '받았어요').
 *
 * <p>상황: 선송금·환급은 앱이 은행 이체를 대신 해 주지 않는다. 사용자가 각자 은행 앱에서
 * 돈을 보낸 뒤 "보냈어요"를 누르면, 그 사실(상태)만 기록한다.
 *
 * <p>권한(해당 이체의 송금자 본인)은 로그인 사용자가 필요하므로 Controller 는 인증 후 붙인다.
 * 지금은 "송금자 memberId 가 주어졌다"고 보고 상태 변경 로직만 완성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;

    /**
     * 송금자가 '보냈어요'를 누른다 (API-39). PENDING → SENT.
     *
     * <ul>
     *   <li>송금자 본인이 아니거나 없는 이체면 404 (남의 이체 존재 여부를 숨긴다).</li>
     *   <li>PENDING 이고 수취 계좌가 연결돼 있으면 SENT 로 기록한다.</li>
     *   <li>수취 계좌가 아직 없으면 409 ACCOUNT_NOT_REGISTERED.</li>
     *   <li>이미 SENT/RECEIVED 면 버튼을 다시 눌러도 현재 상태를 그대로 반환(멱등).</li>
     *   <li>CANCELLED 면 409 TRANSFER_CANCELLED.</li>
     * </ul>
     *
     * <p>TODO(정책/인증 후): PREPAY 는 양측 팟 참여 확정 후, REFUND 는 정산 확정 후에만 허용.
     * 계좌가 방 종료로 만료됐으면 410 ACCOUNT_EXPIRED. 실제 은행 이체는 실행하지 않는다.
     *
     * @param payerMemberId 송금자(팟 참여) ID — 인증 연결 전까지는 호출자가 넘겨준다
     * @param transferId    대상 이체 ID
     */
    @Transactional
    public TransferResponse markSent(Long payerMemberId, Long transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(t -> t.getPayerMemberId().equals(payerMemberId))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        switch (transfer.getStatus()) {
            case PENDING -> {
                if (transfer.getReceiverAccountId() == null) {
                    throw new OwllyException(ErrorCode.ACCOUNT_NOT_REGISTERED);
                }
                transfer.markSent(Instant.now());
            }
            case SENT, RECEIVED -> { /* 이미 처리됨: 멱등, 현재 상태 그대로 반환 */ }
            case CANCELLED -> throw new OwllyException(ErrorCode.TRANSFER_CANCELLED);
        }
        return TransferResponse.from(transfer);
    }
}
