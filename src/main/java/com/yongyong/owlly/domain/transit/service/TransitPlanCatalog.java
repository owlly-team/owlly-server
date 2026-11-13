package com.yongyong.owlly.domain.transit.service;

import com.yongyong.owlly.domain.fare.dto.GeoPoint;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 막차 경로를 안내할 수 있는 구간 목록(거점 + 도착 구 → 버스 노선과 승·하차 정류장).
 *
 * <p>GBIS 는 노선별 막차 시각만 주고 "A에서 B까지 어떤 버스를 타나"는 알려 주지 않아서, 안내할 구간을 미리 적어 둔다.
 * 지금은 시연 구간 둘이다: 단국대 정문 거점(이름으로 찾는다) → 영통구, 강남대 정문 → 수지구(임시) → 수원시 영통구, 720-3번(순환, 종점 단국대에서 영통역 방향).
 * 목록에 없는 구간은 막차 안내 없이(available=false) 응답한다.
 */
@Component
public class TransitPlanCatalog {

    /** 영통구 공식 코드(경기도 수원시 영통구). 하차 동의 부모 구와 맞춰 본다. */
    static final String YEONGTONG_DISTRICT_CODE = "4111700000";

    /** 수지구 공식 코드(경기도 용인시 수지구). */
    static final String SUJI_DISTRICT_CODE = "4146500000";

    /** 강남대 구간의 출발 거점 이름(시드로 넣은 이름). */
    static final String KANGNAM_PICKUP_POINT_NAME = "강남대 정문";

    /** 시연 구간의 출발 거점 이름. 번호는 DB마다 달라서 시드로 넣은 이름으로 찾는다. */
    private final String demoPickupPointName;

    public TransitPlanCatalog(@Value("${owlly.transit.demo.pickup-point-name:단국대 정문}") String demoPickupPointName) {
        this.demoPickupPointName = demoPickupPointName;
    }

    /** 이 이름의 거점에서 이 구로 가는 막차 경로. 없으면 빈 값. 예) "단국대 정문" → 영통구. */
    public Optional<TransitPlan> find(String pickupPointName, String districtOfficialCode) {
        if (demoPickupPointName.equals(pickupPointName)
                && YEONGTONG_DISTRICT_CODE.equals(districtOfficialCode)) {
            return Optional.of(DANKOOK_TO_YEONGTONG);
        }
        if (KANGNAM_PICKUP_POINT_NAME.equals(pickupPointName)
                && SUJI_DISTRICT_CODE.equals(districtOfficialCode)) {
            return Optional.of(KANGNAM_TO_SUJI);
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

    /**
     * 강남대 시연 구간(임시): 강남대 정문 → 수지구, 33번 마을버스(기흥역4번출구 기점, 미금역3번출구 종점).
     * 정류장 이름·좌표·순서는 GBIS 경유정류소(33번: 강남대입구 4번째 → 죽전동성2차아파트 37번째)에서 가져왔다.
     * 기점 출발 막차(평일 23:30)가 기점에서 세 정류장 뒤인 강남대입구에 서는 걸로 본다.
     * 기점에서 정류장까지 분, 소요 분, 요금은 GBIS 에 없어서 임시로 적은 값이다(기획·실측으로 확인 필요).
     */
    static final TransitPlan KANGNAM_TO_SUJI = new TransitPlan(
            241424001L,                                                    // GBIS 노선 번호(33번)
            "33",
            false,                                                         // 기점(기흥역4번출구) 출발 막차를 쓴다
            "강남대입구", new GeoPoint(37.27145, 127.1274667),
            "죽전동성2차아파트", new GeoPoint(37.3285833, 127.1092167),
            33,                                                            // 37 - 4
            5,                                                             // 기점 출발 → 강남대입구 도착(임시)
            25,                                                            // 강남대입구 → 죽전동성2차아파트 버스 소요(임시)
            1_250                                                          // 마을버스 교통카드 요금(임시)
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
