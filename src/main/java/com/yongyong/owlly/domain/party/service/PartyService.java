package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.common.util.Masking;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.domain.chat.service.ChatRoomService;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.notification.dto.NotificationMessage;
import com.yongyong.owlly.domain.notification.entity.NotificationType;
import com.yongyong.owlly.domain.notification.service.NotificationService;
import com.yongyong.owlly.domain.party.dto.PartyAction;
import com.yongyong.owlly.domain.party.dto.PartyMemberResponse;
import com.yongyong.owlly.domain.party.dto.PartyMemberRow;
import com.yongyong.owlly.domain.party.dto.PartyResponse;
import com.yongyong.owlly.domain.party.entity.BoardingStatus;
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
import com.yongyong.owlly.domain.region.service.OutOfCityDetector;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import com.yongyong.owlly.domain.settlement.service.SettlementEstimateService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
 * 학생은 이 화면에서 팟을 확정하거나 빠지고, 출발할 때는 채팅방에서 탔는지 기록한다.
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

    /** 탑승 확인은 출발 1시간 뒤까지 (화면정의서 S-06). */
    static final Duration BOARDING_OPEN_AFTER_DEPARTURE = Duration.ofHours(1);

    /** 탑승을 기록할 수 있는 팟 상태: 선송금·출발 대기·이동 중. 확정 전이거나 해산·정산 단계 팟은 제외. */
    private static final Set<PartyStatus> BOARDABLE_STATUSES =
            Set.of(PartyStatus.PREPAYING, PartyStatus.READY, PartyStatus.RIDING);

    private final RidePartyRepository ridePartyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyStopRepository partyStopRepository;
    private final PickupPointRepository pickupPointRepository;
    private final RideRequestRepository rideRequestRepository;
    private final JourneyRepository journeyRepository;
    private final ChatRoomService chatRoomService;
    private final TaxiFareService taxiFareService;
    private final OutOfCityDetector outOfCityDetector;
    private final SettlementEstimateService settlementEstimateService;
    private final NotificationService notificationService;
    private final EncryptedValueReader encryptedValueReader;
    private final PlaceNameReader placeNameReader;

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
        PickupPoint pickupPointEntity = pickupPointRepository.findById(party.getPickupPointId())
                .orElseThrow(() -> new IllegalStateException("출발 거점이 없습니다. id=" + party.getPickupPointId()));
        PickupPointResponse pickupPoint = PickupPointResponse.of(pickupPointEntity,
                placeNameReader.universityNames(Collections.singletonList(pickupPointEntity.getUniversityId()))
                        .get(pickupPointEntity.getUniversityId()));
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
                        row.genderSnapshot(),
                        Objects.equals(row.memberId(), party.getRepresentativeMemberId()),
                        row.decision(),
                        row.boardingStatus(),
                        stopOrderByMemberId.get(row.memberId())))
                .toList();

        return new PartyResponse(
                party.getId(),
                party.getStatus(),
                pickupPoint,
                party.getDistrictId(),
                placeNameReader.areaNames(Collections.singletonList(party.getDistrictId())).get(party.getDistrictId()),
                party.getDepartureAt(),
                confirmDeadline(party),
                party.getRepresentativeMemberId(),
                me.memberId(),
                members,
                party.getRouteVersion(),
                party.getTaxiCalledAt(),
                roomId,
                allowedActions(party, me.decision(), me.boardingStatus(), now,
                        Objects.equals(me.memberId(), party.getRepresentativeMemberId())));
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
     * 이미 확정한 재시도가 아니면 If-Match 가 지금 팟 ETag 와 같은지 먼저 본다(다르면 412).
     */
    @Transactional
    public PartyResponse confirmParty(Long userId, Long partyId, Instant now, String ifMatch) {
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
        requireNotChanged(userId, partyId, now, ifMatch);
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
        chatRoomService.openRoom(partyId);   // 확정한 사람부터 바로 입장: 전원을 기다리지 않는다(기획 답변 v2 #9). 이미 열려 있으면 그대로 둔다
        if (everyoneConfirmed) {
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
     * 남은 사람의 예상 분담액은 새 경로의 전체 예상 요금으로 정산 쪽 reEstimate 가 다시 계산한다
     * (화면정의서 S-06 더보기 "남은 사람 예상 분담액 다시 계산"). 이미 보낸 선송금은 바꾸지 않는다.
     * 이미 빠진 재시도가 아니면 If-Match 가 지금 팟 ETag 와 같은지 먼저 본다(다르면 412).
     */
    @Transactional
    public PartyResponse leaveParty(Long userId, Long partyId, Instant now, String ifMatch) {
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
        requireNotChanged(userId, partyId, now, ifMatch);
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
        chatRoomService.memberLeft(partyId, me.getId());

        List<PartyMember> remaining = members.stream().filter(PartyMember::isActive).toList();
        boolean representativeLeft = me.getId().equals(party.getRepresentativeMemberId());
        if (representativeLeft || remaining.size() < PartyGrouper.MIN_PARTY_SIZE) {
            disband(party, remaining, requestById, userIdByMemberId, representativeLeft);
        } else {
            int newTotalWon = rerouteWithout(party, List.of(me), members, requestById, now);
            settlementEstimateService.reEstimate(party.getId(), newTotalWon);
        }
        return getParty(userId, partyId, now);
    }

    /** 팟을 해산하고, 남은 사람의 신청을 매칭 실패로 바꿔 알린다(화면정의서 S-04 매칭 실패). */
    private void disband(RideParty party, List<PartyMember> remaining, Map<Long, RideRequest> requestById,
                         Map<Long, Long> userIdByMemberId, boolean representativeLeft) {
        party.cancel();
        chatRoomService.closeRoom(party.getId(), Instant.now());
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
    private int rerouteWithout(RideParty party, List<PartyMember> leavers, List<PartyMember> members,
                                Map<Long, RideRequest> requestById, Instant now) {
        Set<Long> leaverIds = leavers.stream().map(PartyMember::getId).collect(Collectors.toSet());
        Map<Long, Long> requestIdByMemberId = members.stream()
                .collect(Collectors.toMap(PartyMember::getId, PartyMember::getRequestId));
        List<Long> dropOrder = partyStopRepository
                .findByPartyIdAndRouteVersionOrderByStopOrderAsc(party.getId(), party.getRouteVersion()).stream()
                .map(PartyStop::getMemberId)
                .filter(memberId -> !leaverIds.contains(memberId))
                .toList();

        PickupPoint pickupPoint = pickupPointRepository.findById(party.getPickupPointId())
                .orElseThrow(() -> new IllegalStateException("출발 거점이 없습니다. id=" + party.getPickupPointId()));
        GeoPoint origin = new GeoPoint(pickupPoint.getLatitude().doubleValue(), pickupPoint.getLongitude().doubleValue());
        List<GeoPoint> stops = dropOrder.stream()
                .map(memberId -> encryptedValueReader.readDropoff(requestById.get(requestIdByMemberId.get(memberId))))
                .toList();
        boolean outOfCity = outOfCityDetector.isOutOfCity(pickupPoint.getAreaId(), dropOrder.stream()
                .map(memberId -> requestById.get(requestIdByMemberId.get(memberId)).getDistrictId())
                .toList());   // 남은 사람 중 하나라도 거점과 다른 시·군이면 시계 외 할증
        RouteEstimate route = taxiFareService.estimateRoute(origin, stops, party.getDepartureAt(), outOfCity);

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
        return route.totalFareWon();
    }

    /**
     * 확정 마감이 지난 팟 번호들(확정 대기 CONFIRMING 인 팟만). 확정 마감 처리 작업이 1분마다 찾는다.
     * 확정 마감이 비어 있는 팟은 출발 15분 전을 마감으로 본다(confirmDeadline 과 같은 기준).
     */
    public List<Long> findPartyIdsPastConfirmDeadline(Instant now) {
        return ridePartyRepository.findPartyIdsPastConfirmDeadline(
                now, now.plus(MatchingService.CONFIRM_DEADLINE_BEFORE_DEPARTURE));
    }

    /** 확정 마감 때 팟을 어떻게 처리할지. */
    enum DeadlineOutcome {
        DISBAND_REPRESENTATIVE_NOT_CONFIRMED,   // 대표가 확정하지 않음 → 해산(대표 승계 없음)
        DISBAND_TOO_FEW,                        // 확정한 사람이 2명 미만 → 해산
        CONTINUE_WITH_CONFIRMED                 // 확정한 사람끼리 진행
    }

    /**
     * 확정 마감이 지났을 때의 결정(기획 답변 v2 #9, 화면정의서 S-05).
     * 대표가 확정하지 않았으면 몇 명이 확정했든 해산하고, 대표가 확정했으면 확정한 사람이 2명 이상일 때 진행한다.
     *
     * @param confirmedCount 확정(ACCEPTED)한 사람 수. 대표 포함
     */
    static DeadlineOutcome decideAtConfirmDeadline(boolean representativeConfirmed, int confirmedCount) {
        if (!representativeConfirmed) {
            return DeadlineOutcome.DISBAND_REPRESENTATIVE_NOT_CONFIRMED;
        }
        return confirmedCount < PartyGrouper.MIN_PARTY_SIZE
                ? DeadlineOutcome.DISBAND_TOO_FEW
                : DeadlineOutcome.CONTINUE_WITH_CONFIRMED;
    }

    /**
     * 확정 마감 처리(서버 내부 작업). 마감까지 확정하지 않은 멤버는 팟에서 빠지고(신청은 매칭 실패),
     * 대표가 확정하지 않았거나 확정한 사람이 2명 미만이면 팟을 해산한다. 그 외에는 확정한 사람끼리 경로를 다시 계산해
     * 선송금 단계(PREPAYING)로 넘어간다. 확정한 사람은 확정할 때 이미 채팅방에 들어가 있다(API-23).
     * - 확정 대기(CONFIRMING)가 아니거나 마감 전이면 아무것도 하지 않는다(이미 처리됐거나 전원이 확정한 팟).
     * - 빠진 멤버에게 따로 알림은 보내지 않는다(화면정의서에 문구가 없다). 해산이면 남은 사람에게 기존 해산 알림을 보낸다.
     *
     * @return 이 팟을 처리했으면 true
     */
    @Transactional
    public boolean closeConfirmation(Long partyId, Instant now) {
        RideParty party = ridePartyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null || party.getStatus() != PartyStatus.CONFIRMING || !now.isAfter(confirmDeadline(party))) {
            return false;
        }
        Map<Long, Long> userIdByMemberId = partyMemberRepository.findRowsByPartyId(partyId).stream()
                .collect(Collectors.toMap(PartyMemberRow::memberId, PartyMemberRow::userId));
        List<PartyMember> members = partyMemberRepository.findByPartyIdOrderByIdAsc(partyId);
        Map<Long, RideRequest> requestById = rideRequestRepository.findAllById(
                        members.stream().map(PartyMember::getRequestId).toList()).stream()
                .collect(Collectors.toMap(RideRequest::getId, Function.identity()));

        List<PartyMember> unconfirmed = members.stream()
                .filter(member -> member.getDecision() == MemberDecision.PENDING)
                .toList();
        List<PartyMember> confirmed = members.stream()
                .filter(member -> member.getDecision() == MemberDecision.ACCEPTED)
                .toList();
        boolean representativeConfirmed = confirmed.stream()
                .anyMatch(member -> member.getId().equals(party.getRepresentativeMemberId()));

        for (PartyMember member : unconfirmed) {      // 마감까지 확정하지 않은 사람은 팟에서 빠진다
            member.leave(now);
            requestById.get(member.getRequestId()).fail();
        }
        switch (decideAtConfirmDeadline(representativeConfirmed, confirmed.size())) {
            case DISBAND_REPRESENTATIVE_NOT_CONFIRMED -> disband(party, confirmed, requestById, userIdByMemberId, true);
            case DISBAND_TOO_FEW -> disband(party, confirmed, requestById, userIdByMemberId, false);
            case CONTINUE_WITH_CONFIRMED -> {
                if (!unconfirmed.isEmpty()) {
                    int newTotalWon = rerouteWithout(party, unconfirmed, members, requestById, now);
                    settlementEstimateService.reEstimate(party.getId(), newTotalWon);
                }
                party.startPrepaying();
            }
        }
        return true;
    }

    /**
     * 대표의 외부 택시 호출 확인 (API-25). 화면정의서 S-06 대표 · 받을 금액 확인의 ④ "택시를 불렀어요".
     * - 대표만 누를 수 있다. 대표가 아닌 참여자는 403 FORBIDDEN
     * - 이미 눌렀으면 같은 응답(호출 시각은 한 번만 기록)
     * - 선송금 단계(PREPAYING) 팟이 아니면 409 INVALID_STATE
     * - 호출 시각을 기록하고 팟을 출발 대기(READY)로 바꾼 뒤, 남은 멤버에게 알린다(화면정의서 ④ "멤버에게 알림")
     * - 선송금을 받았는지는 확인하지 않는다. 입금 확인은 대표가 은행 앱에서 직접 한다
     *   (기획 답변 5 "송금 확인을 앱에서 하지 않는 구조", 화면정의서 S-06 대표 · 받을 금액 확인 정책)
     * 실제 택시 호출이나 호출 사실 검증은 하지 않는다(api.md).
     * 이미 기록한 재시도가 아니면 If-Match 가 지금 팟 ETag 와 같은지 먼저 본다(다르면 412).
     */
    @Transactional
    public PartyResponse confirmTaxiCall(Long userId, Long partyId, Instant now, String ifMatch) {
        RideParty party = ridePartyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        List<PartyMemberRow> rows = partyMemberRepository.findRowsByPartyId(partyId);
        PartyMemberRow me = rows.stream()
                .filter(row -> row.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!me.memberId().equals(party.getRepresentativeMemberId())) {
            throw new OwllyException(ErrorCode.FORBIDDEN);
        }
        if (party.getTaxiCalledAt() != null) {
            return getParty(userId, partyId, now);
        }
        requireNotChanged(userId, partyId, now, ifMatch);
        if (party.getStatus() != PartyStatus.PREPAYING) {
            throw new OwllyException(ErrorCode.INVALID_STATE);
        }

        party.markTaxiCalled(now);
        chatRoomService.taxiCalled(partyId);
        String representativeName = Masking.holderName(encryptedValueReader.readText(me.realNameEncrypted()));
        rows.stream()
                .filter(row -> row.decision() == MemberDecision.ACCEPTED)
                .filter(row -> !row.memberId().equals(party.getRepresentativeMemberId()))
                .forEach(row -> notificationService.send(new NotificationMessage(
                        row.userId(),
                        party.getId(),
                        NotificationType.SYSTEM,
                        "taxi-called:member:" + row.memberId(),
                        representativeName + "님이 택시를 불렀어요",   // 기획 답변 v2 #13: "{대표}님이 택시를 불렀어요"
                        "타면 채팅방에서 탔어요를 눌러 주세요",
                        "/parties/" + party.getId() + "/room",
                        null)));
        return getParty(userId, partyId, now);
    }

    /**
     * 본인 탑승·미탑승 기록 (API-26). 채팅방 탑승 확인의 "탔어요"·"못 탔어요".
     * - 확정한 멤버가 탑승 확인 시간 안에만 기록할 수 있고, 아니면 409 BOARDING_NOT_ALLOWED
     * - 같은 값을 다시 보내면 같은 응답, 다른 값으로 바꾸려 하면 409 BOARDING_ALREADY_RECORDED
     * - 탔어요(BOARDED): 내 귀가 결과를 동승(POOL_BOARDED)으로 기록하고, 팟이 아직 출발 전 단계면 이동 중(RIDING)으로
     * - 못 탔어요(MISSED): 기록만 한다. 환급·제재는 하지 않고 정산은 전원 탄 것으로 계산한다(기획 답변 2)
     */
    @Transactional
    public PartyResponse recordBoarding(Long userId, Long partyId, BoardingStatus status, Instant now) {
        if (status == BoardingStatus.UNKNOWN) {
            throw new OwllyException(ErrorCode.VALIDATION_ERROR);
        }
        RideParty party = ridePartyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        Long myMemberId = partyMemberRepository.findRowsByPartyId(partyId).stream()
                .filter(row -> row.userId().equals(userId))
                .map(PartyMemberRow::memberId)
                .findFirst()
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        PartyMember me = partyMemberRepository.findById(myMemberId).orElseThrow();

        if (me.getBoardingStatus() == status) {
            return getParty(userId, partyId, now);
        }
        if (me.getBoardingStatus() != BoardingStatus.UNKNOWN) {
            throw new OwllyException(ErrorCode.BOARDING_ALREADY_RECORDED);
        }
        if (!canRecordBoarding(party, me.getDecision(), now)) {
            throw new OwllyException(ErrorCode.BOARDING_NOT_ALLOWED);
        }

        me.recordBoarding(status, now);
        if (status == BoardingStatus.BOARDED) {
            RideRequest request = rideRequestRepository.findById(me.getRequestId()).orElseThrow();
            journeyRepository.findById(request.getJourneyId()).orElseThrow().markPoolBoarded(now);
            if (party.getStatus() != PartyStatus.RIDING) {
                party.startRiding();
            }
        }
        return getParty(userId, partyId, now);
    }

    /**
     * 학생이 마지막으로 본 팟(If-Match)이 지금 팟과 같은지 확인한다. 다르면 412 PRECONDITION_FAILED.
     * 예) 매칭 결과 화면을 열어 둔 사이 대표가 빠져 팟이 해산됐는데 "확정하기"를 누른 경우, 다시 조회하게 한다.
     * 이미 처리된 재시도는 이 확인 전에 같은 응답으로 끝난다(api.md 1절 "완료된 멱등 요청을 먼저 확인한 뒤 ETag 를 검사").
     * ifMatch 가 null 이면(서버 내부 호출) 확인하지 않는다. Controller 는 If-Match 를 필수로 받는다.
     */
    private void requireNotChanged(Long userId, Long partyId, Instant now, String ifMatch) {
        if (ifMatch != null && !ETags.matches(ifMatch, getParty(userId, partyId, now))) {
            throw new OwllyException(ErrorCode.PRECONDITION_FAILED);
        }
    }

    /**
     * 지금 누를 수 있는 버튼 (화면정의서 S-05 버튼 정책).
     * <ul>
     *   <li>CONFIRM: 확정 대기 중인 팟에서 내가 아직 결정 전(PENDING)이고 확정 마감 전.
     *       마감이 지나면 빠져서 화면은 "확정 시간이 지났어요"를 보여 준다.</li>
     *   <li>LEAVE: 내가 결정 전이거나 확정했고, 택시를 타기 전 팟이고, 출발 15분 전까지.</li>
     *   <li>CONFIRM_TAXI_CALL: 내가 대표이고, 선송금 단계 팟에서 아직 택시 호출을 기록하지 않았을 때.</li>
     *   <li>RECORD_BOARDING: 내가 확정했고 아직 탑승 여부를 기록하지 않았고, 탑승 확인 시간 안(화면정의서 S-06).</li>
     * </ul>
     *
     * @param amRepresentative 이 화면을 보는 학생이 대표인지
     */
    static List<PartyAction> allowedActions(RideParty party, MemberDecision myDecision,
                                            BoardingStatus myBoarding, Instant now,
                                            boolean amRepresentative) {
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

        if (amRepresentative
                && party.getStatus() == PartyStatus.PREPAYING
                && party.getTaxiCalledAt() == null) {
            actions.add(PartyAction.CONFIRM_TAXI_CALL);
        }

        if (canRecordBoarding(party, myDecision, now) && myBoarding == BoardingStatus.UNKNOWN) {
            actions.add(PartyAction.RECORD_BOARDING);
        }
        return actions;
    }

    /**
     * 탑승 여부를 기록할 수 있는지: 내가 확정한 멤버이고, 해산·정산 전 팟이고, 탑승 확인 시간 안.
     * 탑승 확인 시간은 출발 15분 전(빠지기 마감, 이때부터 집결 단계)부터 출발 1시간 뒤까지로 본다
     * (화면정의서 S-06 "탑승 확인은 출발 1시간 안에만 가능").
     */
    static boolean canRecordBoarding(RideParty party, MemberDecision myDecision, Instant now) {
        Instant opensAt = party.getDepartureAt().minus(LEAVE_DEADLINE_BEFORE_DEPARTURE);
        Instant closesAt = party.getDepartureAt().plus(BOARDING_OPEN_AFTER_DEPARTURE);
        return myDecision == MemberDecision.ACCEPTED
                && BOARDABLE_STATUSES.contains(party.getStatus())
                && !now.isBefore(opensAt)
                && !now.isAfter(closesAt);
    }

    /** 확정 마감. 저장된 값이 비어 있으면 막차 후 기준(출발 15분 전)으로 본다(기획 답변 4). */
    static Instant confirmDeadline(RideParty party) {
        return (party.getConfirmDeadlineAt() != null)
                ? party.getConfirmDeadlineAt()
                : party.getDepartureAt().minus(MatchingService.CONFIRM_DEADLINE_BEFORE_DEPARTURE);
    }
}
