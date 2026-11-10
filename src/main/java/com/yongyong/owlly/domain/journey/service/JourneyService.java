package com.yongyong.owlly.domain.journey.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.JourneyPage;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyChoiceMode;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 귀가 (journey).
 *
 * <p>상황: 학생이 앱을 열면 홈이 "지금 진행 중인 동승이 있나?"를 확인해 동승 카드를 붙일지 정하고,
 * 내 정보에서는 지난 귀가 기록을 최근 순으로 보여 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JourneyService {

    /** 진행 중인 신청으로 보는 상태: 매칭 대기·매칭됨. 실패·취소·만료는 끝난 신청이다. */
    private static final List<RideRequestStatus> ACTIVE_REQUEST_STATUSES =
            List.of(RideRequestStatus.WAITING, RideRequestStatus.MATCHED);

    private final JourneyRepository journeyRepository;
    private final RideRequestRepository rideRequestRepository;
    private final PlaceNameReader placeNameReader;

    /**
     * 내 귀가 기록 (API-16). 최근 귀가부터 limit 개씩.
     * - cursor 가 없으면 첫 페이지, 있으면 그다음부터.
     * - 각 귀가에 진행 중인 신청 id 를 붙인다. 예) 매칭 대기 중이면 activeRequestId 로 신청·매칭 결과를 조회할 수 있다.
     * - 학교·거점·하차 동 이름을 붙인다. 한 페이지의 id 를 모아 종류마다 한 번씩만 읽는다.
     */
    public JourneyPage getMyJourneys(Long userId, String cursor, int limit) {
        // 한 개 더 가져와서 다음 페이지가 있는지 판단한다.
        PageRequest fetch = PageRequest.of(0, limit + 1);
        List<Journey> rows;
        if (cursor == null) {
            rows = journeyRepository.findMyFirstPage(userId, fetch);
        } else {
            JourneyCursor c = JourneyCursor.decode(cursor);
            rows = journeyRepository.findMyNextPage(userId, c.departureAt(), c.id(), fetch);
        }

        boolean hasMore = rows.size() > limit;
        List<Journey> page = hasMore ? rows.subList(0, limit) : rows;
        if (page.isEmpty()) {
            return new JourneyPage(List.of(), null, false);
        }

        Map<Long, Long> activeRequestIdByJourney = findActiveRequestIds(page);
        Map<Long, String> universityNames = placeNameReader.universityNames(
                page.stream().map(Journey::getUniversityId).toList());
        Map<Long, String> pickupPointNames = placeNameReader.pickupPointNames(
                page.stream().map(Journey::getPickupPointId).toList());
        Map<Long, String> dongNames = placeNameReader.areaNames(
                page.stream().map(Journey::getDestinationDongId).toList());
        List<JourneyResponse> journeys = page.stream()
                .map(j -> JourneyResponse.of(j, activeRequestIdByJourney.get(j.getId()),
                        universityNames.get(j.getUniversityId()),
                        pickupPointNames.get(j.getPickupPointId()),
                        dongNames.get(j.getDestinationDongId())))
                .toList();
        String nextCursor = hasMore ? JourneyCursor.encode(page.get(page.size() - 1)) : null;
        return new JourneyPage(journeys, nextCursor, hasMore);
    }

    /**
     * 막차 또는 혼자 택시 선택 기록 (API-17). 매칭에 실패했거나 동승을 포기한 학생이 귀가 방법을 정할 때.
     * - 본인 귀가가 아니거나 없는 귀가면 똑같이 404 RESOURCE_NOT_FOUND(남의 귀가가 "있다"는 사실도 숨긴다).
     * - 같은 선택을 다시 보내면 현재 결과를 그대로 돌려준다(버튼을 두 번 눌러도 문제없게).
     * - 이미 다른 결과가 정해졌거나(탑승·다른 선택) 취소된 귀가면 409 JOURNEY_ALREADY_RESOLVED. 덮어쓰지 않는다.
     * - 매칭 대기(WAITING) 신청이 있으면 같은 트랜잭션에서 취소한 뒤 기록한다.
     * - 이미 팟에 배정된(MATCHED) 신청이 있으면 409 PARTY_LEAVE_REQUIRED: 팟에서 먼저 나가야 한다.
     * 학생의 연속 클릭, 매칭 작업과 동시에 바뀌지 않도록 귀가와 진행 중인 신청을 잠근 뒤 확인한다.
     */
    @Transactional
    public JourneyResponse chooseOutcome(Long userId, Long journeyId, JourneyChoiceMode mode, Instant now) {
        Journey journey = journeyRepository.findByIdForUpdate(journeyId)
                .filter(j -> j.getUserId().equals(userId))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        if (journey.getOutcome() == mode.outcome()) {
            return toResponse(journey, null);
        }
        if (journey.getOutcome() != JourneyOutcome.UNRESOLVED) {
            throw new OwllyException(ErrorCode.JOURNEY_ALREADY_RESOLVED);
        }

        List<RideRequest> activeRequests = rideRequestRepository
                .findByJourneyIdAndStatusInForUpdate(journeyId, ACTIVE_REQUEST_STATUSES);
        if (activeRequests.stream().anyMatch(r -> r.getStatus() == RideRequestStatus.MATCHED)) {
            throw new OwllyException(ErrorCode.PARTY_LEAVE_REQUIRED);
        }
        activeRequests.forEach(RideRequest::cancel);

        journey.chooseByUser(mode, now);
        return toResponse(journey, null);
    }

    /** 귀가 한 건의 응답에 학교·거점·하차 동 이름을 붙인다. */
    private JourneyResponse toResponse(Journey journey, Long activeRequestId) {
        return JourneyResponse.of(journey, activeRequestId,
                placeNameReader.universityNames(List.of(journey.getUniversityId())).get(journey.getUniversityId()),
                placeNameReader.pickupPointNames(List.of(journey.getPickupPointId())).get(journey.getPickupPointId()),
                placeNameReader.areaNames(List.of(journey.getDestinationDongId())).get(journey.getDestinationDongId()));
    }

    /** 귀가별 진행 중인 신청 id. 귀가당 최대 1개지만 혹시 여러 개면 가장 최근(id 큰) 것. */
    private Map<Long, Long> findActiveRequestIds(List<Journey> journeys) {
        List<Long> journeyIds = journeys.stream().map(Journey::getId).toList();
        return rideRequestRepository.findByJourneyIdInAndStatusIn(journeyIds, ACTIVE_REQUEST_STATUSES).stream()
                .collect(Collectors.toMap(RideRequest::getJourneyId, RideRequest::getId, Math::max));
    }
}
