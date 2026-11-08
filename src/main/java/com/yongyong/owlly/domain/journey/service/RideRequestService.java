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

        return RideRequestResponse.of(request, partyId);
    }

    /** 신청 → 귀가 → 학생으로 이어서 본인 것인지 확인한다(신청에는 학생 id 가 없다). */
    private boolean isOwnedBy(RideRequest request, Long userId) {
        return journeyRepository.findById(request.getJourneyId())
                .map(journey -> journey.getUserId().equals(userId))
                .orElse(false);
    }
}
