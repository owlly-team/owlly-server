package com.yongyong.owlly.domain.transit.service;

import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 막차 경로를 안내할 수 있는 구간 목록(거점 + 도착 구 → 버스 노선과 승·하차 정류장).
 *
 * <p>GBIS 는 노선별 막차 시각만 주고 "A에서 B까지 어떤 버스를 타나"는 알려 주지 않아서, 안내할 구간을 미리 적어 둔다.
 * 지금은 시연 구간 하나뿐이다: 단국대 정문 거점 → 수원시 영통구, 720-3번(순환, 종점 단국대에서 영통역 방향).
 * 목록에 없는 구간은 막차 안내 없이(available=false) 응답한다.
 */
@Component
public class TransitPlanCatalog {

    /** 영통구 공식 코드(경기도 수원시 영통구). 하차 동의 부모 구와 맞춰 본다. */
    static final String YEONGTONG_DISTRICT_CODE = "4111700000";

    private final long demoPickupPointId;

    public TransitPlanCatalog(@Value("${owlly.transit.demo.pickup-point-id:11}") long demoPickupPointId) {
        this.demoPickupPointId = demoPickupPointId;
    }

    /** 이 거점에서 이 구로 가는 막차 경로. 없으면 빈 값. */
    public Optional<TransitPlan> find(Long pickupPointId, String districtOfficialCode) {
        if (pickupPointId != null && pickupPointId == demoPickupPointId
                && YEONGTONG_DISTRICT_CODE.equals(districtOfficialCode)) {
            return Optional.of(DANKOOK_TO_YEONGTONG);
        }
        return Optional.empty();
    }

    /**
     * 시연 구간. 정류장 이름·좌표·정류장 수는 GBIS 노선 경유정류소(720-3: 단국대정문 81번째 → 영통역8번출구 122번째)에서 가져왔다.
     * 소요 분과 요금은 GBIS 에 없어서 임시로 적은 값이다(기획·실측으로 확인 필요).
     */
    static final TransitPlan DANKOOK_TO_YEONGTONG = new TransitPlan(
            234000068L,                                                    // GBIS 노선 번호
            "720-3",
            true,                                                          // 종점(단국대.치과병원) 출발 막차를 쓴다
            "단국대정문", new GeoPoint(37.3233333, 127.12585),
            "영통역8번출구", new GeoPoint(37.25235, 127.0719167),
            41,                                                            // 122 - 81
            5,                                                             // 종점 출발 → 정문 정류장 도착(임시)
            40,                                                            // 정문 → 영통역 버스 소요(임시)
            1_550                                                          // 일반형 시내버스 교통카드 요금(화면정의서 예시)
    );

    /** 막차 경로 한 구간. */
    public record TransitPlan(
            long routeId,                  // GBIS 노선 번호
            String routeName,              // 노선 이름
            boolean fromTerminal,          // true 면 종점 출발 막차, false 면 기점 출발 막차
            String boardStopName,          // 타는 정류장 이름
            GeoPoint boardStop,            // 타는 정류장 위치
            String alightStopName,         // 내리는 정류장 이름
            GeoPoint alightStop,           // 내리는 정류장 위치
            int stopCount,                 // 타는 정류장에서 내리는 정류장까지 정류장 수
            int minutesFromTerminalToBoard, // 막차가 출발한 뒤 타는 정류장에 오기까지 분(임시)
            int rideMinutes,               // 타는 정류장에서 내리는 정류장까지 분(임시)
            int fareWon                    // 1인 교통카드 요금(원)
    ) {}
}
