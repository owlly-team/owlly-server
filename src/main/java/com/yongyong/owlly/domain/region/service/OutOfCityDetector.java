package com.yongyong.owlly.domain.region.service;

import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 택시 경로가 시·군 경계를 넘는지 판단한다(시계 외 할증 대상).
 *
 * <p>거점과 하차 지점이 속한 시·군(CITY)을 행정구역 계층에서 찾아 비교한다. 경계의 정확한 위치는 보지 않고,
 * 하차 지점 중 하나라도 거점과 다른 시·군이면 경로 전체가 시계 외라고 어림한다(혼자 택시·팟 예상 요금이 같은 규칙을 쓴다).
 * 예) 단국대 정문 거점(용인시) → 영통구(수원시): 시계 외. 같은 용인시 안: 시계 외 아님.
 */
@Component
@RequiredArgsConstructor
public class OutOfCityDetector {

    /** 동 → 구 → 시처럼 위로 올라가 시·군을 찾을 때 최대 단계. */
    private static final int MAX_HOPS = 4;

    private final AdministrativeAreaRepository administrativeAreaRepository;

    /**
     * @param pickupAreaId       출발 거점의 행정구역 번호
     * @param destinationAreaIds 하차 지점의 행정구역 번호들(구·동 어느 단계든 된다)
     * @return 하차 지점 중 하나라도 거점과 다른 시·군이면 true. 거점의 시·군을 알 수 없으면 false(할증을 붙이지 않는다)
     */
    public boolean isOutOfCity(Long pickupAreaId, Collection<Long> destinationAreaIds) {
        Optional<AdministrativeArea> pickupCity = cityOf(pickupAreaId);
        if (pickupCity.isEmpty()) {
            return false;
        }
        return destinationAreaIds.stream()
                .map(this::cityOf)
                .flatMap(Optional::stream)
                .anyMatch(city -> !city.getId().equals(pickupCity.get().getId()));
    }

    /** 이 행정구역이 속한 시·군(CITY). 위로 거슬러 올라가며 찾고, 못 찾으면 빈 값. */
    private Optional<AdministrativeArea> cityOf(Long areaId) {
        Long currentId = areaId;
        for (int hops = 0; currentId != null && hops < MAX_HOPS; hops++) {
            Optional<AdministrativeArea> found = administrativeAreaRepository.findById(currentId);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            if (found.get().getLevel() == AreaLevel.CITY) {
                return found;
            }
            currentId = found.get().getParentId();
        }
        return Optional.empty();
    }
}
