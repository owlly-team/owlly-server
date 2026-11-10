package com.yongyong.owlly.domain.journey.service;

import com.yongyong.owlly.domain.journey.dto.JourneyPage;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
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

    /** 귀가별 진행 중인 신청 id. 귀가당 최대 1개지만 혹시 여러 개면 가장 최근(id 큰) 것. */
    private Map<Long, Long> findActiveRequestIds(List<Journey> journeys) {
        List<Long> journeyIds = journeys.stream().map(Journey::getId).toList();
        return rideRequestRepository.findByJourneyIdInAndStatusIn(journeyIds, ACTIVE_REQUEST_STATUSES).stream()
                .collect(Collectors.toMap(RideRequest::getJourneyId, RideRequest::getId, Math::max));
    }
}
