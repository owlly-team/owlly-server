package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.domain.settlement.dto.AccountInput;
import com.yongyong.owlly.domain.settlement.dto.MaskedAccount;
import com.yongyong.owlly.domain.settlement.entity.AccountPurpose;
import com.yongyong.owlly.domain.settlement.entity.RoomAccount;
import com.yongyong.owlly.domain.settlement.repository.RoomAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수취 계좌 등록 (API-31 PUT /rooms/{roomId}/accounts/{purpose}).
 *
 * <p>상황: 선송금·환급을 받을 계좌를 채팅방에 등록한다. 계좌번호·예금주는 암호화해서 저장하고,
 * 응답은 마스킹해서 돌려준다. 같은 방·멤버·용도의 계좌는 1개라, 다시 등록하면 교체(upsert)한다.
 *
 * <p>권한(용도별 대표/본인)·방 열림·기존 이체 잠금은 인증/정책이 필요하므로 아래 TODO 로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomAccountService {

    private final RoomAccountRepository roomAccountRepository;
    private final TextCryptor textCryptor;

    /**
     * 수취 계좌를 등록(또는 교체)한다.
     *
     * <p>흐름: 입력 정규화 → 암호화 → upsert(있으면 교체/없으면 생성) → 마스킹 응답.
     *
     * <p>TODO(인증/정책 후):
     * <ul>
     *   <li>방이 OPEN 인지 확인(아니면 ROOM_CLOSED).</li>
     *   <li>PREPAY_RECEIVE 는 대표만, REFUND_RECEIVE 는 본인만(ACCOUNT_PURPOSE_NOT_ALLOWED).</li>
     *   <li>이 계좌로 SENT/RECEIVED 된 이체가 있으면 덮어쓰기 금지(ACCOUNT_LOCKED).</li>
     *   <li>미송금 PENDING 이체에 수취 계좌 연결.</li>
     * </ul>
     *
     * @param roomId        채팅방 ID
     * @param purpose       계좌 용도(PREPAY_RECEIVE/REFUND_RECEIVE)
     * @param ownerMemberId 계좌 소유 멤버 ID — 인증 연결 전까지는 호출자가 넘겨준다
     * @param input         은행·계좌번호·예금주
     */
    @Transactional
    public MaskedAccount register(Long roomId, AccountPurpose purpose, Long ownerMemberId, AccountInput input) {
        // 1) 입력 정규화: 계좌번호의 공백·하이픈 제거, 앞뒤 공백 정리
        String bankCode = input.bankCode().strip();
        String accountNumber = input.accountNumber().replaceAll("[\\s-]", "");
        String holderName = input.holderName().strip();

        // 2) 저장 전 암호화 (원문은 DB 에 남기지 않는다)
        String encryptedNumber = textCryptor.encrypt(accountNumber);
        String encryptedHolder = textCryptor.encrypt(holderName);

        // 3) upsert: 같은 방·멤버·용도 계좌가 있으면 교체, 없으면 새로 저장
        RoomAccount account = roomAccountRepository
                .findByRoomIdAndOwnerMemberIdAndPurpose(roomId, ownerMemberId, purpose)
                .map(existing -> {
                    existing.changeAccount(bankCode, encryptedNumber, encryptedHolder); // 더티 체킹으로 UPDATE
                    return existing;
                })
                .orElseGet(() -> roomAccountRepository.save(RoomAccount.builder()
                        .roomId(roomId)
                        .ownerMemberId(ownerMemberId)
                        .purpose(purpose)
                        .bankCode(bankCode)
                        .accountNumberEncrypted(encryptedNumber)
                        .holderNameEncrypted(encryptedHolder)
                        .build()));

        // 4) 응답은 마스킹 (원문은 가린 뒤에만 사용)
        return MaskedAccount.of(account, accountNumber, holderName);
    }
}
