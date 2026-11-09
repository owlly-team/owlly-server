package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.settlement.dto.SettlementResponse;
import com.yongyong.owlly.domain.settlement.dto.TransferResponse;
import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import com.yongyong.owlly.domain.settlement.entity.Transfer;
import com.yongyong.owlly.domain.settlement.repository.SettlementItemRepository;
import com.yongyong.owlly.domain.settlement.repository.SettlementRepository;
import com.yongyong.owlly.domain.settlement.repository.TransferRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팟 정산 조회 (API-33 GET /parties/{partyId}/settlement).
 *
 * <p>상황: 택시에서 내린 뒤 "내가 얼마 돌려받지?" 를 확인할 때 호출된다.
 * 정산 1건 + 그 아래 개인 항목들 + 각 항목의 송금들을 한 덩어리로 묶어 응답한다.
 *
 * <p>권한(대표는 전체, 일반 멤버는 본인 항목만)은 로그인한 사용자가 필요하므로,
 * 인증 기능이 들어온 뒤 Controller 와 함께 붙인다. 지금은 조회·조립 로직만 완성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final TransferRepository transferRepository;

    /**
     * 팟의 정산 내역을 조회한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>partyId 로 정산 1건을 찾는다. 아직 없으면 409 SETTLEMENT_NOT_READY.</li>
     *   <li>그 정산의 개인 항목들을 찾는다.</li>
     *   <li>항목 ID들로 송금을 한 번에 가져와 항목별로 묶는다(N+1 방지).</li>
     *   <li>항목마다 자기 송금을 끼워 DTO 로 조립한다.</li>
     * </ol>
     *
     * @param partyId 조회할 팟 ID
     * @return 정산 응답(항목·송금 포함)
     */
    public SettlementResponse getSettlement(Long partyId) {
        // 1) 정산 1건. 생성 전이면 409.
        Settlement settlement = settlementRepository.findByPartyId(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.SETTLEMENT_NOT_READY));

        // 2) 이 정산의 개인별 항목들.
        List<SettlementItem> itemEntities =
                settlementItemRepository.findBySettlementIdOrderByIdAsc(settlement.getId());

        // 3) 항목 ID들로 송금을 한 번에 조회 → 항목 ID 기준으로 분류(Map).
        List<Long> itemIds = itemEntities.stream().map(SettlementItem::getId).toList();
        Map<Long, List<Transfer>> transfersByItemId =
                transferRepository.findBySettlementItemIdInOrderByIdAsc(itemIds).stream()
                        .collect(Collectors.groupingBy(Transfer::getSettlementItemId));

        // 4) 항목마다 자기 송금을 DTO 로 바꿔 끼운다. 송금 없는 항목은 빈 목록.
        List<SettlementResponse.Item> items = itemEntities.stream()
                .map(item -> {
                    List<TransferResponse> transfers =
                            transfersByItemId.getOrDefault(item.getId(), List.of()).stream()
                                    .map(TransferResponse::from)
                                    .toList();
                    return SettlementResponse.Item.from(item, transfers);
                })
                .toList();

        // 5) 전체를 하나로 조립.
        return SettlementResponse.from(settlement, items);
    }
}
