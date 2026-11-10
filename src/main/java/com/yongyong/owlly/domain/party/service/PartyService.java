package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.util.Masking;
import com.yongyong.owlly.domain.chat.service.ChatRoomService;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.notification.dto.NotificationMessage;
import com.yongyong.owlly.domain.notification.entity.NotificationType;
import com.yongyong.owlly.domain.notification.service.NotificationService;
import com.yongyong.owlly.domain.party.dto.PartyAction;
import com.yongyong.owlly.domain.party.dto.PartyMemberResponse;
import com.yongyong.owlly.domain.party.dto.PartyMemberRow;
import com.yongyong.owlly.domain.party.dto.PartyResponse;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.party.entity.PartyMember;
import com.yongyong.owlly.domain.party.entity.PartyStatus;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.region.dto.PickupPointResponse;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동승 팟 (party).
 *
 * <p>상황: 매칭 알림이나 매칭 대기 카드를 누르면 매칭 결과 화면(S-05)이 열린다.
 * 같이 탈 사람(이름 일부·학교·하차 동·동성 여부), 내리는 순서, 대표, 그리고 지금 누를 수 있는 버튼을 보여 준다.
 * 학생은 이 화면에서 팟을 확정하거나 빠진다.
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
    private final RideRequestRepository rideRequestRepository;
    private final ChatRoomService chatRoomService;
    private final TaxiFareService taxiFareService;
    private final NotificationService notificationService;
    private final EncryptedValueReader encryptedValueReader;

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
                        Masking.holderName(encryptedValueReader.readText(row.realNameEncrypted())),
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
     * 본인 팟 확정 (API-23). 매칭 결과 화면의 "이 팟 확정하기".
     * - 내 결정 PENDING → ACCEPTED
     * - 이미 확정했으면 같은 응답(버튼을 두 번 눌러도 문제없게)
     * - 이미 거절·이탈했거나 확정 단계가 아닌 팟이면 409 INVALID_STATE
     * - 확정 마감이 지났으면 409 CONFIRMATION_DEADLINE_PASSED
     * - 남은 멤버가 전원 확정하면 채팅방을 열고 선송금 단계로 넘긴다(화면정의서 S-05 화면 정책).
     *   마감 시각이 됐을 때 확정한 사람끼리 여는 것은 확정 마감 처리 작업이 한다.
     * 마지막 두 사람이 동시에 눌러도 전원 확정을 놓치지 않도록 팟을 잠근 뒤 확인한다.
     */
    @Transactional
    public PartyResponse confirmParty(Long userId, Long partyId, Instant now) {
        RideParty party = ridePartyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        Long myMemberId = partyMemberRepository.findRowsByPartyId(partyId).stream()
                .filter(row -> row.userId().equals(userId))
                .map(PartyMemberRow::memberId)
                .findFirst()
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        List<PartyMember> members = partyMemberRepository.findByPartyIdOrderByIdAsc(partyId);
        PartyMember me = members.stream()
                .filter(member -> member.getId().equals(myMemberId))
                .findFirst()
                .orElseThrow();

        if (me.getDecision() == MemberDecision.ACCEPTED) {
            return getParty(userId, partyId, now);
        }
        if (me.getDecision() != MemberDecision.PENDING || party.getStatus() != PartyStatus.CONFIRMING) {
            throw new OwllyException(ErrorCode.INVALID_STATE);
        }
        if (now.isAfter(confirmDeadline(party))) {
            throw new OwllyException(ErrorCode.CONFIRMATION_DEADLINE_PASSED);
        }

        me.confirm(now);
        boolean everyoneConfirmed = members.stream()
                .filter(PartyMember::isActive)
                .allMatch(member -> member.getDecision() == MemberDecision.ACCEPTED);
        if (everyoneConfirmed) {
            chatRoomService.openRoom(partyId);
            party.startPrepaying();
        }
        return getParty(userId, partyId, now);
    }

    /**
     * 팟 거절·이탈 (API-24). 매칭 결과 화면·채팅방의 "빠지기".
     * - 확정 전이면 DECLINED, 확정 뒤면 LEFT. 내 신청은 CANCELLED
     * - 이미 빠졌으면 같은 응답(버튼을 두 번 눌러도 문제없게)
     * - 택시를 탄 뒤나 해산된 팟이면 409 INVALID_STATE
     * - 출발 15분 전이 지났으면 409 CANCELLATION_DEADLINE_PASSED
     * - 내가 보냈거나 받은 송금이 이미 있으면 409 FUNDS_ALREADY_IN_FLIGHT (돈이 얽히면 자동 처리 금지, api.md)
     * - 대표가 빠지거나 남은 사람이 1명이면 팟 해산. 남은 사람 신청은 매칭 실패로 바꾸고 알림을 보낸다
     *   (기획 답변 3, 화면정의서 S-05 ⑦)
     * - 2명 이상 남으면 빠진 사람만 지운 하차 순서로 경로를 다시 계산해 새 경로 버전으로 저장한다.
     *   대표(마지막 하차자)는 그대로다.
     * 예상 정산 다시 계산은 정산 쪽 예상 정산이 채워지면 붙인다.
     */
    @Transactional
    public PartyResponse leaveParty(Long userId, Long partyId, Instant now) {
        RideParty party = ridePartyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        Map<Long, Long> userIdByMemberId = partyMemberRepository.findRowsByPartyId(partyId).stream()
                .collect(Collectors.toMap(PartyMemberRow::memberId, PartyMemberRow::userId));
        List<PartyMember> members = partyMemberRepository.findByPartyIdOrderByIdAsc(partyId);
        PartyMember me = members.stream()
                .filter(member -> userId.equals(userIdByMemberId.get(member.getId())))
                .findFirst()
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!me.isActive()) {
            return getParty(userId, partyId, now);
        }
        if (!LEAVABLE_STATUSES.contains(party.getStatus())) {
            throw new OwllyException(ErrorCode.INVALID_STATE);
        }
        if (now.isAfter(party.getDepartureAt().minus(LEAVE_DEADLINE_BEFORE_DEPARTURE))) {
            throw new OwllyException(ErrorCode.CANCELLATION_DEADLINE_PASSED);
        }
        if (ridePartyRepository.existsFundsInFlight(me.getId())) {
            throw new OwllyException(ErrorCode.FUNDS_ALREADY_IN_FLIGHT);
        }

        Map<Long, RideRequest> requestById = rideRequestRepository.findAllById(
                        members.stream().map(PartyMember::getRequestId).toList()).stream()
                .collect(Collectors.toMap(RideRequest::getId, Function.identity()));
        me.leave(now);
        requestById.get(me.getRequestId()).cancel();

        List<PartyMember> remaining = members.stream().filter(PartyMember::isActive).toList();
        boolean representativeLeft = me.getId().equals(party.getRepresentativeMemberId());
        if (representativeLeft || remaining.size() < PartyGrouper.MIN_PARTY_SIZE) {
            disband(party, remaining, requestById, userIdByMemberId, representativeLeft);
        } else {
            rerouteWithout(party, me, members, requestById, now);
        }
        return getParty(userId, partyId, now);
    }

    /** 팟을 해산하고, 남은 사람의 신청을 매칭 실패로 바꿔 알린다(화면정의서 S-04 매칭 실패). */
    private void disband(RideParty party, List<PartyMember> remaining, Map<Long, RideRequest> requestById,
                         Map<Long, Long> userIdByMemberId, boolean representativeLeft) {
        party.cancel();
        String title = representativeLeft ? "대표가 빠져서 이번 동승이 취소됐어요" : "이번엔 매칭되지 않았어요";
        for (PartyMember member : remaining) {
            RideRequest request = requestById.get(member.getRequestId());
            request.fail();
            notificationService.send(new NotificationMessage(
                    userIdByMemberId.get(member.getId()),
                    party.getId(),
                    NotificationType.MATCH_RESULT,
                    "party-cancelled:request:" + request.getId(),
                    title,
                    "막차나 혼자 택시로 귀가 방법을 다시 골라 주세요",
                    "/ride-requests/" + request.getId(),
                    null));
        }
    }

    /**
     * 빠진 사람만 지운 하차 순서로 구간 거리를 다시 받아 새 경로 버전으로 저장한다.
     * 처음부터 다시 정렬하지 않는 이유: 마지막 하차자(대표)가 바뀌면 안 되기 때문이다(대표 승계 없음).
     */
    private void rerouteWithout(RideParty party, PartyMember leaver, List<PartyMember> members,
                                Map<Long, RideRequest> requestById, Instant now) {
        Map<Long, Long> requestIdByMemberId = members.stream()
                .collect(Collectors.toMap(PartyMember::getId, PartyMember::getRequestId));
        List<Long> dropOrder = partyStopRepository
                .findByPartyIdAndRouteVersionOrderByStopOrderAsc(party.getId(), party.getRouteVersion()).stream()
                .map(PartyStop::getMemberId)
                .filter(memberId -> !memberId.equals(leaver.getId()))
                .toList();

        PickupPoint pickupPoint = pickupPointRepository.findById(party.getPickupPointId())
                .orElseThrow(() -> new IllegalStateException("출발 거점이 없습니다. id=" + party.getPickupPointId()));
        GeoPoint origin = new GeoPoint(pickupPoint.getLatitude().doubleValue(), pickupPoint.getLongitude().doubleValue());
        List<GeoPoint> stops = dropOrder.stream()
                .map(memberId -> encryptedValueReader.readDropoff(requestById.get(requestIdByMemberId.get(memberId))))
                .toList();
        RouteEstimate route = taxiFareService.estimateRoute(origin, stops, party.getDepartureAt());

        int newRouteVersion = party.getRouteVersion() + 1;
        List<PartyStop> newStops = new ArrayList<>();
        for (int i = 0; i < dropOrder.size(); i++) {
            newStops.add(PartyStop.builder()
                    .partyId(party.getId())
                    .memberId(dropOrder.get(i))
                    .routeVersion(newRouteVersion)
                    .stopOrder(i + 1)
                    .segmentDistanceM(route.segmentDistancesM().get(i))
                    .routingSource(route.routingSource())
                    .calculatedAt(now)
                    .build());
        }
        partyStopRepository.saveAll(newStops);
        party.changeRoute(newRouteVersion);
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
}
