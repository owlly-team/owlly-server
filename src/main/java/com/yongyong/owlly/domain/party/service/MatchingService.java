package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import com.yongyong.owlly.domain.fare.dto.RouteEstimate;
import com.yongyong.owlly.domain.fare.service.TaxiFareService;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.notification.dto.NotificationMessage;
import com.yongyong.owlly.domain.notification.entity.NotificationType;
import com.yongyong.owlly.domain.notification.service.NotificationService;
import com.yongyong.owlly.domain.party.dto.MatchingCandidate;
import com.yongyong.owlly.domain.party.dto.MatchingGroupKey;
import com.yongyong.owlly.domain.party.entity.BoardingStatus;
import com.yongyong.owlly.domain.party.entity.MemberDecision;
import com.yongyong.owlly.domain.party.entity.PartyMember;
import com.yongyong.owlly.domain.party.entity.PartyStatus;
import com.yongyong.owlly.domain.party.entity.PartyStop;
import com.yongyong.owlly.domain.party.entity.RideParty;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.party.repository.PartyStopRepository;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.party.service.PartyGrouper.UserPair;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.settlement.dto.MemberEstimate;
import com.yongyong.owlly.domain.settlement.service.SettlementEstimateService;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 작업 (서버 내부 작업, API 아님).
 *
 * <p>상황: 결과 안내 시각이 된 대기 신청을 같은 조건(학교·거점·출발 시각·도착 구)끼리 모아 2~4명 팟으로 묶는다.
 * 팟·멤버·하차 순서·대표를 저장하고, 예상 정산을 만든 뒤, 성공·실패 알림을 남긴다(api.md 2절, erd.md 6절).
 * 학생이나 관계자가 직접 매칭을 확정하는 공개 API는 두지 않는다.
 *
 * <p>결과 안내 시각·확정 마감(기획 답변)
 * <ul>
 *   <li>막차 전 등록: 확정 마감 = 막차 타러 출발해야 할 시각, 결과 안내 = 출발 20분 전과 확정 마감 5분 전 중 이른 시각.</li>
 *   <li>막차 후 등록: 결과 안내 = 출발 20분 전, 확정 마감 = 출발 15분 전(방 나가기 마감과 같음).</li>
 * </ul>
 * 막차 정보(GBIS) 연동 전이라 결과 안내 시각이 비어 있으면 막차 후 기준으로 처리한다.
 * 하차 좌표는 복호화해서 읽고, 확인 코드는 암호화해서 저장한다(공통 TextCryptor).
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    static final Duration RESULT_DUE_BEFORE_DEPARTURE = Duration.ofMinutes(20);
    static final Duration CONFIRM_DEADLINE_BEFORE_DEPARTURE = Duration.ofMinutes(15);
    static final int FIRST_ROUTE_VERSION = 1;
    static final String SEED_PLAINTEXT_PREFIX = "TEST:";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RideRequestRepository rideRequestRepository;
    private final JourneyRepository journeyRepository;
    private final PickupPointRepository pickupPointRepository;
    private final RidePartyRepository ridePartyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyStopRepository partyStopRepository;
    private final TaxiFareService taxiFareService;
    private final SettlementEstimateService settlementEstimateService;
    private final NotificationService notificationService;
    private final TextCryptor textCryptor;

    /** 지금 처리할 그룹(결과 안내 시각이 된 대기 신청이 있는 매칭 키). */
    @Transactional(readOnly = true)
    public List<MatchingGroupKey> findDueGroups(Instant now) {
        return rideRequestRepository.findDueGroupKeys(now, now.plus(RESULT_DUE_BEFORE_DEPARTURE));
    }

    /**
     * 한 그룹을 매칭한다. 그룹마다 따로 트랜잭션이라, 한 그룹이 실패해도 다른 그룹은 진행된다.
     * 중간에 실패하면 이 그룹의 팟·상태 변경·알림이 모두 남지 않는다.
     *
     * @return 이번에 만든 팟 수
     */
    @Transactional
    public int matchGroup(MatchingGroupKey key, Instant now) {
        List<RideRequest> requests = rideRequestRepository.findDueInGroupForUpdate(
                key.universityId(), key.pickupPointId(), key.districtId(), key.departureAt(),
                now, now.plus(RESULT_DUE_BEFORE_DEPARTURE));
        if (requests.isEmpty()) {
            return 0;
        }

        Map<Long, RideRequest> requestById = requests.stream()
                .collect(Collectors.toMap(RideRequest::getId, Function.identity()));
        Map<Long, Long> userIdByJourneyId = journeyRepository.findAllById(
                        requests.stream().map(RideRequest::getJourneyId).distinct().toList()).stream()
                .collect(Collectors.toMap(Journey::getId, Journey::getUserId));

        List<MatchingCandidate> candidates = requests.stream()
                .map(r -> new MatchingCandidate(r.getId(), userIdByJourneyId.get(r.getJourneyId()),
                        r.getGenderSnapshot(), r.isSameGenderOnly()))
                .toList();
        Set<UserPair> blockedPairs = ridePartyRepository.findBlockedPairs(
                        candidates.stream().map(MatchingCandidate::userId).distinct().toList()).stream()
                .map(p -> UserPair.of(p.getLowUserId(), p.getHighUserId()))
                .collect(Collectors.toSet());

        PartyGrouper.Result result = PartyGrouper.group(candidates, blockedPairs);
        PickupPoint pickupPoint = pickupPointRepository.findById(key.pickupPointId())
                .orElseThrow(() -> new IllegalStateException("출발 거점이 없습니다. id=" + key.pickupPointId()));

        for (List<MatchingCandidate> members : result.parties()) {
            createParty(key, pickupPoint, members, requestById, now);
        }
        for (MatchingCandidate alone : result.unmatched()) {
            RideRequest request = requestById.get(alone.requestId());
            request.fail();
            notificationService.send(failureMessage(alone, request));
        }
        return result.parties().size();
    }

    /** 팟 → 멤버 → 하차 순서 → 대표 순서로 저장하고, 예상 정산·알림까지 만든다. */
    private void createParty(MatchingGroupKey key, PickupPoint pickupPoint, List<MatchingCandidate> members,
                             Map<Long, RideRequest> requestById, Instant now) {
        RideParty party = ridePartyRepository.save(RideParty.builder()
                .universityId(key.universityId())
                .pickupPointId(key.pickupPointId())
                .districtId(key.districtId())
                .departureAt(key.departureAt())
                .verificationCodeEncrypted(newVerificationCode())
                .routeVersion(FIRST_ROUTE_VERSION)
                .confirmDeadlineAt(key.departureAt().minus(CONFIRM_DEADLINE_BEFORE_DEPARTURE))
                .status(PartyStatus.FORMING)
                .build());

        List<PartyMember> savedMembers = partyMemberRepository.saveAll(members.stream()
                .map(m -> PartyMember.builder()
                        .partyId(party.getId())
                        .requestId(m.requestId())
                        .decision(MemberDecision.PENDING)
                        .boardingStatus(BoardingStatus.UNKNOWN)
                        .build())
                .toList());

        GeoPoint origin = new GeoPoint(pickupPoint.getLatitude().doubleValue(), pickupPoint.getLongitude().doubleValue());
        Map<Long, GeoPoint> dropoffByMemberId = new HashMap<>();
        for (PartyMember member : savedMembers) {
            dropoffByMemberId.put(member.getId(), readDropoff(requestById.get(member.getRequestId())));
        }
        List<PartyMember> dropOrder = orderByNearestDropoff(origin, savedMembers, dropoffByMemberId);
        RouteEstimate route = taxiFareService.estimateRoute(
                origin, dropOrder.stream().map(m -> dropoffByMemberId.get(m.getId())).toList(), key.departureAt());

        List<PartyStop> stops = new ArrayList<>();
        for (int i = 0; i < dropOrder.size(); i++) {
            stops.add(PartyStop.builder()
                    .partyId(party.getId())
                    .memberId(dropOrder.get(i).getId())
                    .routeVersion(FIRST_ROUTE_VERSION)
                    .stopOrder(i + 1)
                    .segmentDistanceM(route.segmentDistancesM().get(i))
                    .routingSource(route.routingSource())
                    .calculatedAt(now)
                    .build());
        }
        partyStopRepository.saveAll(stops);
        party.assignRepresentative(dropOrder.get(dropOrder.size() - 1).getId());

        Map<Long, Integer> shareByMemberId = settlementEstimateService
                .createEstimate(party.getId(), route.totalFareWon()).stream()
                .collect(Collectors.toMap(MemberEstimate::memberId, MemberEstimate::estimatedShareWon));

        Map<Long, Long> userIdByRequestId = members.stream()
                .collect(Collectors.toMap(MatchingCandidate::requestId, MatchingCandidate::userId));
        for (PartyMember member : savedMembers) {
            requestById.get(member.getRequestId()).match();
            notificationService.send(successMessage(party, pickupPoint, savedMembers.size(), member,
                    userIdByRequestId.get(member.getRequestId()), shareByMemberId.get(member.getId())));
        }
    }

    /** 거점에서 가장 가까운 하차 지점부터, 그다음엔 직전 하차 지점에서 가장 가까운 곳으로 이어 간다. */
    private static List<PartyMember> orderByNearestDropoff(GeoPoint origin, List<PartyMember> members,
                                                           Map<Long, GeoPoint> dropoffByMemberId) {
        List<PartyMember> remaining = new ArrayList<>(members);
        List<PartyMember> ordered = new ArrayList<>();
        GeoPoint current = origin;
        while (!remaining.isEmpty()) {
            GeoPoint from = current;
            PartyMember next = remaining.stream()
                    .min((a, b) -> Double.compare(
                            TaxiFareService.straightDistanceM(from, dropoffByMemberId.get(a.getId())),
                            TaxiFareService.straightDistanceM(from, dropoffByMemberId.get(b.getId()))))
                    .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
            current = dropoffByMemberId.get(next.getId());
        }
        return ordered;
    }

    /** 신청의 하차 좌표를 복호화해서 읽는다. */
    private GeoPoint readDropoff(RideRequest request) {
        try {
            return new GeoPoint(readCoordinate(request.getDropoffLatEncrypted()),
                    readCoordinate(request.getDropoffLngEncrypted()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("하차 좌표를 읽을 수 없습니다. requestId=" + request.getId(), e);
        }
    }

    /**
     * 저장된 좌표 한 값. 시드·테스트 데이터의 평문 값("TEST:37.2510")은 그대로 읽고, 그 외는 복호화한다.
     * 동승 신청 API 가 생기면 실제 신청 좌표는 모두 암호화되어 저장된다.
     */
    private double readCoordinate(String stored) {
        if (stored.startsWith(SEED_PLAINTEXT_PREFIX)) {
            return Double.parseDouble(stored.substring(SEED_PLAINTEXT_PREFIX.length()).trim());
        }
        return Double.parseDouble(textCryptor.decrypt(stored));
    }

    /** 같은 팟 확인용 4자리 코드(0000~9999)를 암호화한 값. */
    private String newVerificationCode() {
        return textCryptor.encrypt(String.format("%04d", RANDOM.nextInt(10_000)));
    }

    private static NotificationMessage successMessage(RideParty party, PickupPoint pickupPoint, int partySize,
                                                      PartyMember member, Long userId, Integer shareWon) {
        String body = toKstTime(party.getDepartureAt()) + " " + pickupPoint.getName() + " 출발"
                + (shareWon == null ? "" : " · 예상 1인 " + NumberFormat.getInstance(Locale.KOREA).format(shareWon) + "원")
                + " · " + toKstTime(party.getConfirmDeadlineAt()) + "까지 확정해 주세요";
        return new NotificationMessage(
                userId,
                party.getId(),
                NotificationType.MATCH_RESULT,
                "match-result:request:" + member.getRequestId(),
                "같은 학교 " + partySize + "명과 매칭됐어요",
                body,
                "/parties/" + party.getId(),
                null);
    }

    private static NotificationMessage failureMessage(MatchingCandidate alone, RideRequest request) {
        return new NotificationMessage(
                alone.userId(),
                null,
                NotificationType.MATCH_RESULT,
                "match-result:request:" + request.getId(),
                "이번엔 매칭되지 않았어요",
                "막차나 혼자 택시로 귀가 방법을 다시 골라 주세요",
                "/ride-requests/" + request.getId(),
                null);
    }

    private static String toKstTime(Instant at) {
        return at.atZone(KST).format(HH_MM);
    }
}
