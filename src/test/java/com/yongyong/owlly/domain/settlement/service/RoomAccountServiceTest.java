package com.yongyong.owlly.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.settlement.dto.AccountInput;
import com.yongyong.owlly.domain.settlement.dto.MaskedAccount;
import com.yongyong.owlly.domain.settlement.dto.RecipientAccount;
import com.yongyong.owlly.domain.settlement.entity.AccountPurpose;
import com.yongyong.owlly.domain.settlement.entity.RoomAccount;
import com.yongyong.owlly.domain.settlement.entity.Transfer;
import com.yongyong.owlly.domain.settlement.entity.TransferKind;
import com.yongyong.owlly.domain.settlement.entity.TransferStatus;
import com.yongyong.owlly.domain.settlement.repository.RoomAccountRepository;
import com.yongyong.owlly.domain.settlement.repository.TransferRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 수취 계좌 등록(API-31)·송금할 계좌 조회(API-32)의 업무 규칙. 저장소·암호화는 가짜로 둔다.
 *
 * <p>암호화 가짜는 "ENC:" 접두사를 붙였다 떼는 방식으로 둬서, 정규화된 원문이 그대로
 * 암호화·복호화되는지 추적할 수 있게 한다.
 */
@ExtendWith(MockitoExtension.class)
class RoomAccountServiceTest {

    @Mock RoomAccountRepository roomAccounts;
    @Mock TransferRepository transfers;
    @Mock TextCryptor textCryptor;
    @InjectMocks RoomAccountService service;

    private static final Long ROOM_ID = 10L;
    private static final Long OWNER = 501L;
    private static final Long TRANSFER_ID = 801L;

    // ---------- API-31 등록 ----------

    @Test
    void 새_계좌면_정규화_암호화해_저장하고_마스킹해_응답한다() {
        given(roomAccounts.findByRoomIdAndOwnerMemberIdAndPurpose(ROOM_ID, OWNER, AccountPurpose.PREPAY_RECEIVE))
                .willReturn(Optional.empty());
        given(textCryptor.encrypt(any())).willAnswer(inv -> "ENC:" + inv.getArgument(0));
        given(roomAccounts.save(any())).willAnswer(inv -> inv.getArgument(0));

        // 계좌번호에 하이픈·공백이 섞여 들어와도 숫자만 남겨 정규화한다
        MaskedAccount res = service.register(ROOM_ID, AccountPurpose.PREPAY_RECEIVE, OWNER,
                new AccountInput("004", " 110-234-567890 ", " 홍길동 "));

        assertThat(res.purpose()).isEqualTo("PREPAY_RECEIVE");
        assertThat(res.bankCode()).isEqualTo("004");
        assertThat(res.maskedAccountNumber()).isEqualTo("110*****7890"); // 앞3·뒤4만
        assertThat(res.maskedHolderName()).isEqualTo("홍*동");

        // 정규화된 원문으로 암호화했는지, 새로 저장했는지 확인
        verify(textCryptor).encrypt("110234567890");
        verify(textCryptor).encrypt("홍길동");
        verify(roomAccounts).save(any(RoomAccount.class));
    }

    @Test
    void 같은_방_멤버_용도의_계좌가_있으면_교체하고_새로_저장하지_않는다() {
        RoomAccount existing = RoomAccount.builder()
                .id(55L).roomId(ROOM_ID).ownerMemberId(OWNER).purpose(AccountPurpose.PREPAY_RECEIVE)
                .bankCode("004").accountNumberEncrypted("ENC:old").holderNameEncrypted("ENC:old")
                .build();
        given(roomAccounts.findByRoomIdAndOwnerMemberIdAndPurpose(ROOM_ID, OWNER, AccountPurpose.PREPAY_RECEIVE))
                .willReturn(Optional.of(existing));
        given(textCryptor.encrypt(any())).willAnswer(inv -> "ENC:" + inv.getArgument(0));

        MaskedAccount res = service.register(ROOM_ID, AccountPurpose.PREPAY_RECEIVE, OWNER,
                new AccountInput("090", "3333-12-4478210", "이정준"));

        // 엔티티가 더티 체킹으로 교체됨(save 호출 없음)
        assertThat(existing.getBankCode()).isEqualTo("090");
        assertThat(existing.getAccountNumberEncrypted()).isEqualTo("ENC:3333124478210");
        assertThat(res.bankCode()).isEqualTo("090");
        assertThat(res.maskedHolderName()).isEqualTo("이*준");
        assertThat(res.maskedAccountNumber()).startsWith("333").endsWith("8210").contains("*");
        verify(roomAccounts, never()).save(any());
    }

    // ---------- API-32 송금할 계좌 조회 ----------

    @Test
    void 송금자에게_복호화한_계좌번호와_마스킹_예금주를_준다() {
        RoomAccount account = RoomAccount.builder()
                .id(77L).roomId(ROOM_ID).ownerMemberId(503L).purpose(AccountPurpose.PREPAY_RECEIVE)
                .bankCode("090").accountNumberEncrypted("ENC:num").holderNameEncrypted("ENC:holder")
                .build();
        given(transfers.findById(TRANSFER_ID)).willReturn(Optional.of(transferWith(OWNER, 77L)));
        given(roomAccounts.findById(77L)).willReturn(Optional.of(account));
        given(textCryptor.decrypt("ENC:num")).willReturn("3333124478210");
        given(textCryptor.decrypt("ENC:holder")).willReturn("이정준");

        RecipientAccount res = service.getRecipientAccount(OWNER, TRANSFER_ID);

        assertThat(res.bankCode()).isEqualTo("090");
        assertThat(res.accountNumber()).isEqualTo("3333124478210"); // 송금자에겐 원문
        assertThat(res.maskedHolderName()).isEqualTo("이*준");        // 예금주는 마스킹
        assertThat(res.purpose()).isEqualTo("PREPAY_RECEIVE");
    }

    @Test
    void 이체가_없거나_송금자가_아니면_404로_숨긴다() {
        // 없는 이체
        given(transfers.findById(TRANSFER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRecipientAccount(OWNER, TRANSFER_ID))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        // 송금자 불일치(남의 이체)
        given(transfers.findById(TRANSFER_ID)).willReturn(Optional.of(transferWith(999L, 77L)));
        assertThatThrownBy(() -> service.getRecipientAccount(OWNER, TRANSFER_ID))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void 수취_계좌가_아직_연결_안됐으면_409_ACCOUNT_NOT_REGISTERED() {
        given(transfers.findById(TRANSFER_ID)).willReturn(Optional.of(transferWith(OWNER, null)));

        assertThatThrownBy(() -> service.getRecipientAccount(OWNER, TRANSFER_ID))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_REGISTERED);
    }

    @Test
    void 계좌가_삭제_만료됐으면_410_ACCOUNT_EXPIRED() {
        given(transfers.findById(TRANSFER_ID)).willReturn(Optional.of(transferWith(OWNER, 77L)));
        given(roomAccounts.findById(77L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecipientAccount(OWNER, TRANSFER_ID))
                .isInstanceOf(OwllyException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_EXPIRED);
    }

    private static Transfer transferWith(Long payerMemberId, Long receiverAccountId) {
        return Transfer.builder()
                .id(TRANSFER_ID)
                .settlementItemId(10001L)
                .kind(TransferKind.PREPAY)
                .payerMemberId(payerMemberId)
                .payeeMemberId(503L)
                .receiverAccountId(receiverAccountId)
                .amountWon(6000)
                .status(TransferStatus.PENDING)
                .build();
    }
}
