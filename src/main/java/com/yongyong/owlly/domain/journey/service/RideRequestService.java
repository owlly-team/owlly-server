package com.yongyong.owlly.domain.journey.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.RideRequestResponse;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.entity.PartyMember;
import com.yongyong.owlly.domain.party.repository.PartyMemberRepository;
import com.yongyong.owlly.domain.region.service.PlaceNameReader;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동승 신청 (ride request).
 *
 * <p>상황: 학생이 동승을 등록하고 홈에서 기다리는 동안, 매칭 대기 카드나 알림에서
 * "매칭됐나?"를 확인한다. 매칭되면 매칭 결과 화면으로, 실패하면 대안 화면으로 넘어간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RideRequestService {

    private final RideRequestRepository rideRequestRepository;
    private final JourneyRepository journeyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PlaceNameReader placeNameReader;

    /**
     * 신청·매칭 결과 조회 (API-20). 본인 신청만 볼 수 있다.
     * - 다른 학생의 신청이거나 없는 신청이면 똑같이 404 RESOURCE_NOT_FOUND
     *   (남의 신청이 "있다"는 사실도 알려 주지 않기 위해, docs/api.md 1절).
     * - 매칭(MATCHED)됐을 때만 배정된 팟 id 를 붙인다.
     */
    public RideRequestResponse getMyRideRequest(Long userId, Long requestId) {
        RideRequest request = rideRequestRepository.findById(requestId)
                .filter(r -> isOwnedBy(r, userId))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        Long partyId = (request.getStatus() == RideRequestStatus.MATCHED)
                ? partyMemberRepository.findByRequestId(request.getId())
                        .map(PartyMember::getPartyId)
                        .orElse(null)
                : null;

        return toResponse(request, partyId);
    }

    /**
     * 대기 신청 취소 (API-21). 매칭 대기 중 "등록 취소"를 누를 때.
     * - WAITING → CANCELLED
     * - 이미 CANCELLED 면 같은 응답(버튼을 두 번 눌러도 문제없게)
     * - MATCHED 면 409 REQUEST_ALREADY_MATCHED: 이미 팟에 배정됐으니 팟 거절·이탈로 처리해야 한다
     * - FAILED·EXPIRED 면 409 INVALID_STATE: 이미 끝난 신청
     * 매칭 작업과 동시에 바뀌지 않도록 신청을 잠근 뒤 확인한다.
     */
    @Transactional
    public RideRequestResponse cancelMyRideRequest(Long userId, Long requestId) {
        RideRequest request = rideRequestRepository.findByIdForUpdate(requestId)
                .filter(r -> isOwnedBy(r, userId))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        switch (request.getStatus()) {
            case WAITING -> request.cancel();
            case CANCELLED -> { /* 이미 취소됨: 그대로 같은 응답 */ }
            case MATCHED -> throw new OwllyException(ErrorCode.REQUEST_ALREADY_MATCHED);
            case FAILED, EXPIRED -> throw new OwllyException(ErrorCode.INVALID_STATE);
        }
        return toResponse(request, null);
    }

    /** 신청 응답에 거점·권역·하차 동 이름을 붙인다. */
    private RideRequestResponse toResponse(RideRequest request, Long partyId) {
        Map<Long, String> areaNames = placeNameReader.areaNames(
                List.of(request.getDistrictId(), request.getDestinationDongId()));
        return RideRequestResponse.of(request, partyId,
                placeNameReader.pickupPointNames(List.of(request.getPickupPointId())).get(request.getPickupPointId()),
                areaNames.get(request.getDistrictId()),
                areaNames.get(request.getDestinationDongId()));
    }

    /** 신청 → 귀가 → 학생으로 이어서 본인 것인지 확인한다(신청에는 학생 id 가 없다). */
    private boolean isOwnedBy(RideRequest request, Long userId) {
        return journeyRepository.findById(request.getJourneyId())
                .map(journey -> journey.getUserId().equals(userId))
                .orElse(false);
    }
}
