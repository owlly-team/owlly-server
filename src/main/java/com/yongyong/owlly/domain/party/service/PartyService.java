package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.util.Masking;
import com.yongyong.owlly.domain.party.dto.PartyAction;
import com.yongyong.owlly.domain.party.dto.PartyMemberResponse;
import com.yongyong.owlly.domain.party.dto.PartyMemberRow;
import com.yongyong.owlly.domain.party.dto.PartyResponse;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.party.entity.PartyStatus;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.region.dto.PickupPointResponse;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동승 팟 (party).
 *
 * <p>상황: 매칭 알림이나 매칭 대기 카드를 누르면 매칭 결과 화면(S-05)이 열린다.
 * 같이 탈 사람(이름 일부·학교·하차 동·동성 여부), 내리는 순서, 대표, 그리고 지금 누를 수 있는 버튼을 보여 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {

    /** 팟에서 빠질 수 있는 마감: 출발 15분 전 (api.md API-24). */
    static final Duration LEAVE_DEADLINE_BEFORE_DEPARTURE = Duration.ofMinutes(15);

    /** 빠지기가 의미 있는 팟 상태: 확정 대기·선송금·출발 대기. 택시를 탄 뒤나 끝난 팟은 빠질 수 없다. */
    private static final Set<PartyStatus> LEAVABLE_STATUSES =
            Set.of(PartyStatus.CONFIRMING, PartyStatus.PREPAYING, PartyStatus.READY);

    private final RidePartyRepository ridePartyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyStopRepository partyStopRepository;
    private final PickupPointRepository pickupPointRepository;
    private final TextCryptor textCryptor;

    /**
     * 팟·멤버·대표·다음 행동 조회 (API-22). 이 팟의 참여자만 볼 수 있다.
     * - 없는 팟이거나 내가 참여자가 아니면 똑같이 404 RESOURCE_NOT_FOUND
     *   (남의 팟이 "있다"는 사실도 알려 주지 않기 위해, docs/api.md 1절).
     * - 채팅방은 내가 확정(ACCEPTED)했을 때만 알려 준다(화면정의서: 확정한 사람끼리 방이 열린다).
     *
     * @param now 버튼 마감을 판단할 현재 시각
     */
    public PartyResponse getParty(Long userId, Long partyId, Instant now) {
        RideParty party = ridePartyRepository.findById(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        List<PartyMemberRow> rows = partyMemberRepository.findRowsByPartyId(partyId);
        PartyMemberRow me = rows.stream()
                .filter(row -> row.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        Map<Long, Integer> stopOrderByMemberId = partyStopRepository
                .findByPartyIdAndRouteVersionOrderByStopOrderAsc(partyId, party.getRouteVersion()).stream()
                .collect(Collectors.toMap(PartyStop::getMemberId, PartyStop::getStopOrder));
        PickupPointResponse pickupPoint = pickupPointRepository.findById(party.getPickupPointId())
                .map(PickupPointResponse::from)
                .orElseThrow(() -> new IllegalStateException("출발 거점이 없습니다. id=" + party.getPickupPointId()));
        Long roomId = (me.decision() == MemberDecision.ACCEPTED)
                ? ridePartyRepository.findRoomIdByPartyId(partyId).orElse(null)
                : null;

        List<PartyMemberResponse> members = rows.stream()
                .map(row -> new PartyMemberResponse(
                        row.memberId(),
                        Masking.holderName(readName(row.realNameEncrypted())),
                        row.universityName(),
                        row.destinationDongName(),
                        row.genderSnapshot() == me.genderSnapshot(),
                        Objects.equals(row.memberId(), party.getRepresentativeMemberId()),
                        row.decision(),
                        row.boardingStatus(),
                        stopOrderByMemberId.get(row.memberId())))
                .toList();

        return new PartyResponse(
                party.getId(),
                party.getStatus(),
                pickupPoint,
                party.getDepartureAt(),
                confirmDeadline(party),
                party.getRepresentativeMemberId(),
                me.memberId(),
                members,
                party.getRouteVersion(),
                party.getTaxiCalledAt(),
                roomId,
                allowedActions(party, me.decision(), now));
    }

    /**
     * 지금 누를 수 있는 버튼 (화면정의서 S-05 버튼 정책).
     * <ul>
     *   <li>CONFIRM: 확정 대기 중인 팟에서 내가 아직 결정 전(PENDING)이고 확정 마감 전.
     *       마감이 지나면 빠져서 화면은 "확정 시간이 지났어요"를 보여 준다.</li>
     *   <li>LEAVE: 내가 결정 전이거나 확정했고, 택시를 타기 전 팟이고, 출발 15분 전까지.</li>
     * </ul>
     */
    static List<PartyAction> allowedActions(RideParty party, MemberDecision myDecision, Instant now) {
        List<PartyAction> actions = new ArrayList<>();

        if (party.getStatus() == PartyStatus.CONFIRMING
                && myDecision == MemberDecision.PENDING
                && !now.isAfter(confirmDeadline(party))) {
            actions.add(PartyAction.CONFIRM);
        }

        Instant leaveDeadline = party.getDepartureAt().minus(LEAVE_DEADLINE_BEFORE_DEPARTURE);
        if (LEAVABLE_STATUSES.contains(party.getStatus())
                && (myDecision == MemberDecision.PENDING || myDecision == MemberDecision.ACCEPTED)
                && !now.isAfter(leaveDeadline)) {
            actions.add(PartyAction.LEAVE);
        }
        return actions;
    }

    /** 확정 마감. 저장된 값이 비어 있으면 막차 후 기준(출발 15분 전)으로 본다(기획 답변 4). */
    static Instant confirmDeadline(RideParty party) {
        return (party.getConfirmDeadlineAt() != null)
                ? party.getConfirmDeadlineAt()
                : party.getDepartureAt().minus(MatchingService.CONFIRM_DEADLINE_BEFORE_DEPARTURE);
    }

    /** 저장된 실명. 시드·테스트 데이터의 평문 값("TEST:김민준")은 그대로 읽고, 그 외는 복호화한다. */
    private String readName(String stored) {
        if (stored.startsWith(MatchingService.SEED_PLAINTEXT_PREFIX)) {
            return stored.substring(MatchingService.SEED_PLAINTEXT_PREFIX.length()).trim();
        }
        return textCryptor.decrypt(stored);
    }
}
