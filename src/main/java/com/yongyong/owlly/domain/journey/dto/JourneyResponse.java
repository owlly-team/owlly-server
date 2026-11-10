package com.yongyong.owlly.domain.journey.dto;

import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.OutcomeSource;
import java.time.Instant;

/**
 * 귀가 한 건의 응답 (docs/api.md 5절 Journey).
 *
 * <p>홈은 이 값으로 동승 카드를 붙일지 정한다.
 * 예) outcome 이 UNRESOLVED 이고 activeRequestId 가 있으면 "매칭 대기 중 / 매칭 결과" 카드를 보여 준다.
 * 내 정보의 지난 귀가 기록에도 같은 모양을 쓴다.
 * id 마다 이름을 같이 보낸다. 프론트는 id 만으로 화면에 이름을 띄울 수 없기 때문이다.
 * ID 는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 */
public record JourneyResponse(
        Long id,                    // 귀가 번호
        Long universityId,          // 귀가를 만들 당시 학교
        String universityName,      // 학교 이름. 예) "단국대학교"
        Long pickupPointId,         // 출발 거점
        String pickupPointName,     // 출발 거점 이름. 예) "단국대 정문 거점"
        Long destinationDongId,     // 하차 행정동
        String destinationDongName, // 하차 행정동 이름. 예) "영통2동"
        Instant intendedDepartureAt, // 귀가 예정 시각(최근 귀가 순 정렬 기준)
        JourneyOutcome outcome,     // UNRESOLVED(아직 결과 전) / POOL_BOARDED / BUS_SELECTED / SOLO_SELECTED / CANCELLED
        Instant outcomeAt,          // 결과가 정해진 시각. 아직이면 null
        OutcomeSource outcomeSource, // 결과 근거(학생 선택 / 탑승 확인). 아직이면 null
        Long activeRequestId        // 진행 중인 동승 신청(WAITING·MATCHED). 없으면 null
) {

    /**
     * @param universityName      학교 이름
     * @param pickupPointName     출발 거점 이름
     * @param destinationDongName 하차 행정동 이름
     */
    public static JourneyResponse of(Journey journey, Long activeRequestId, String universityName,
                                     String pickupPointName, String destinationDongName) {
        return new JourneyResponse(
                journey.getId(),
                journey.getUniversityId(),
                universityName,
                journey.getPickupPointId(),
                pickupPointName,
                journey.getDestinationDongId(),
                destinationDongName,
                journey.getIntendedDepartureAt(),
                journey.getOutcome(),
                journey.getOutcomeAt(),
                journey.getOutcomeSource(),
                activeRequestId);
    }
}
