package com.yongyong.owlly.domain.settlement.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.util.Masking;
import com.yongyong.owlly.domain.party.dto.PartyMemberRow;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.party.service.EncryptedValueReader;
import com.yongyong.owlly.domain.settlement.dto.SettlementResponse;
import com.yongyong.owlly.domain.settlement.dto.TransferResponse;
import com.yongyong.owlly.domain.settlement.entity.Settlement;
import com.yongyong.owlly.domain.settlement.entity.SettlementItem;
import com.yongyong.owlly.domain.settlement.entity.Transfer;
import com.yongyong.owlly.domain.settlement.repository.SettlementItemRepository;
import com.yongyong.owlly.domain.settlement.repository.SettlementRepository;
import com.yongyong.owlly.domain.settlement.repository.TransferRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팟 정산 조회 (API-33 GET /parties/{partyId}/settlement).
 *
 * <p>상황: 택시에서 내린 뒤 "내가 얼마 돌려받지?" 를 확인할 때 호출된다(S-06 정산 내역·멤버).
 * 정산 1건 + 그 아래 개인 항목들 + 각 항목의 송금들을 한 덩어리로 묶어 응답한다.
 *
 * <p>대표·일반 멤버 모두 팟 전원의 개인 항목을 받는다('정산내역-멤버' 화면 요구). 타인
 * 계좌번호는 응답 DTO 에 없으므로 멤버별 필터링 없이 전체를 조회·조립해 반환한다.
 *
 * <p>화면 표시용으로 항목마다 이름(마스킹)·누적 거리·대표/본인 여부를 덧붙인다. 이름·거리는
 * 정산 밖(party 도메인)에 있어 팟 상세와 같은 방식으로 읽어 끼운다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final TransferRepository transferRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyStopRepository partyStopRepository;
    private final RidePartyRepository ridePartyRepository;
    private final EncryptedValueReader encryptedValueReader;

    /**
     * 팟의 정산 내역을 조회한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>partyId 로 정산 1건을 찾는다. 아직 없으면 409 SETTLEMENT_NOT_READY.</li>
     *   <li>그 정산의 개인 항목들을 찾는다.</li>
     *   <li>항목 ID들로 송금을 한 번에 가져와 항목별로 묶는다(N+1 방지).</li>
     *   <li>이름·누적거리·대표를 각각 한 번에 조회해 memberId 기준 Map 으로 만든다.</li>
     *   <li>항목마다 송금·이름·거리·대표/본인 여부를 끼워 DTO 로 조립한다.</li>
     * </ol>
     *
     * @param partyId        조회할 팟 ID
     * @param viewerMemberId 조회자의 팟 참여 멤버 ID(본인 항목 표시용)
     * @return 정산 응답(항목·송금·화면 표시값 포함)
     */
    public SettlementResponse getSettlement(Long partyId, Long viewerMemberId) {
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

        // 4) 화면 표시용 부가값을 memberId 기준 Map 으로 준비.
        Map<Long, String> nameByMemberId = maskedNamesByMemberId(partyId);      // 이름(마스킹)
        Map<Long, Integer> distanceByMemberId =
                cumulativeDistancesByMemberId(partyId, settlement.getRouteVersion()); // 누적 거리(m)
        Long representativeMemberId = ridePartyRepository.findById(partyId)
                .map(RideParty::getRepresentativeMemberId).orElse(null);        // 대표

        // 5) 항목마다 송금·이름·거리·대표/본인 여부를 끼운다. 송금 없는 항목은 빈 목록.
        List<SettlementResponse.Item> items = itemEntities.stream()
                .map(item -> {
                    List<TransferResponse> transfers =
                            transfersByItemId.getOrDefault(item.getId(), List.of()).stream()
                                    .map(TransferResponse::from)
                                    .toList();
                    return SettlementResponse.Item.from(
                            item,
                            transfers,
                            nameByMemberId.get(item.getMemberId()),
                            distanceByMemberId.get(item.getMemberId()),
                            Objects.equals(item.getMemberId(), representativeMemberId),
                            Objects.equals(item.getMemberId(), viewerMemberId));
                })
                .toList();

        // 6) 전체를 하나로 조립.
        return SettlementResponse.from(settlement, items);
    }

    /** 팟 참여자별 마스킹 이름. 암호화 실명을 팟 상세와 같은 방식으로 복호화·마스킹한다. */
    private Map<Long, String> maskedNamesByMemberId(Long partyId) {
        return partyMemberRepository.findRowsByPartyId(partyId).stream()
                .collect(Collectors.toMap(
                        PartyMemberRow::memberId,
                        row -> Masking.holderName(encryptedValueReader.readText(row.realNameEncrypted()))));
    }

    /**
     * 팟 참여자별 '출발지부터 하차까지 누적 거리(m)'. party_stops 의 구간 거리(segment)를
     * 하차 순서(stop_order)대로 누적 합산한다. 정산 계산에 쓴 route_version 기준.
     */
    private Map<Long, Integer> cumulativeDistancesByMemberId(Long partyId, int routeVersion) {
        List<PartyStop> stops =
                partyStopRepository.findByPartyIdAndRouteVersionOrderByStopOrderAsc(partyId, routeVersion);
        Map<Long, Integer> cumulativeByMemberId = new HashMap<>();
        int running = 0;
        for (PartyStop stop : stops) {   // stop_order 오름차순
            running += stop.getSegmentDistanceM();
            cumulativeByMemberId.put(stop.getMemberId(), running);
        }
        return cumulativeByMemberId;
    }
}
