package com.yongyong.owlly.domain.region.dto;

import com.yongyong.owlly.domain.region.entity.PickupPoint;

/**
 * 출발 거점 한 곳의 응답 (docs/api.md 5절 PickupPoint).
 *
 * <p>앱의 "어디서 출발할까요?" 목록에서 한 줄이 된다.
 * 예) "단국대 정문 거점" · "정문 앞 큰길 쪽" · 지도에 찍을 좌표
 *
 * <p>엔티티를 그대로 내보내지 않고, 학생 화면에 필요한 값만 담는다.
 * id·universityId 는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 */
public record PickupPointResponse(
        Long id,               // 거점 번호. 귀가 생성·동승 신청 때 출발 거점으로 보낸다
        Long universityId,     // 학교 전용이면 그 학교 번호, 여러 학교가 같이 쓰는 공용 거점이면 null
        String universityName, // 학교 전용이면 그 학교 이름, 공용 거점이면 null. 예) "단국대학교"
        String name,           // 목록에 보이는 거점 이름. 예) "단국대 정문 거점"
        Point point,           // 지도에 표시할 공개 집결 좌표
        String meetingNote     // "정문 앞 큰길 쪽" 같은 집결 위치 안내
) {

    /** @param universityName 학교 전용 거점이면 그 학교 이름, 공용 거점이면 null */
    public static PickupPointResponse of(PickupPoint entity, String universityName) {
        return new PickupPointResponse(
                entity.getId(),
                entity.getUniversityId(),
                universityName,
                entity.getName(),
                new Point(entity.getLatitude().doubleValue(), entity.getLongitude().doubleValue()),
                entity.getMeetingNote());
    }

    /** 공개 집결 좌표. */
    public record Point(double latitude, double longitude) {}
}
