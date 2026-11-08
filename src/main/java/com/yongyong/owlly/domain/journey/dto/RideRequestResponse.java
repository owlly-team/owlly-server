package com.yongyong.owlly.domain.journey.dto;

import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import java.time.Instant;

/**
 * 동승 신청 한 건과 매칭 결과 (docs/api.md 5절 RideRequest).
 *
 * <p>홈의 매칭 대기 카드가 이 값으로 다음 화면을 정한다.
 * 예) WAITING → 계속 대기 카드, MATCHED → partyId 로 매칭 결과 화면, FAILED → 매칭 실패·대안 화면.
 *
 * <p>하차 좌표와 성별 스냅샷은 학생 화면에 필요 없는 개인정보라 담지 않는다.
 * ID 는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 */
public record RideRequestResponse(
        Long id,                   // 신청 번호. 홈의 activeRequestId 로 이 신청을 조회한다
        Long journeyId,            // 이 신청이 속한 귀가 번호
        Long pickupPointId,        // 이번 신청의 출발 거점
        Long districtId,           // 같은 방향 매칭 권역(구, 구가 없으면 시)
        Long destinationDongId,    // 하차 행정동
        Instant departureAt,       // 30분 단위 출발 시각
        boolean sameGenderOnly,    // 동성끼리만 매칭할지
        RideRequestStatus status,  // WAITING / MATCHED / FAILED / CANCELLED / EXPIRED
        Instant resultDueAt,       // 결과 안내 예정 시각. 아직 정해지지 않았으면 null
        Long partyId,              // 매칭(MATCHED)됐을 때만 배정된 팟. 그 외에는 null
        Instant createdAt          // 등록 시각(먼저 등록한 순서의 기준)
) {

    public static RideRequestResponse of(RideRequest request, Long partyId) {
        return new RideRequestResponse(
                request.getId(),
                request.getJourneyId(),
                request.getPickupPointId(),
                request.getDistrictId(),
                request.getDestinationDongId(),
                request.getDepartureAt(),
                request.isSameGenderOnly(),
                request.getStatus(),
                request.getResultDueAt(),
                partyId,
                request.getCreatedAt());
    }
}
