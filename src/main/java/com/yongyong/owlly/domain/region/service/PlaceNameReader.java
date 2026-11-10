package com.yongyong.owlly.domain.region.service;

import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.university.entity.University;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 응답에 붙일 학교·거점·행정구역 이름을 id 로 한 번에 찾는다. 읽기만 한다.
 *
 * <p>프론트는 id 만으로는 화면에 이름을 띄울 수 없어서, 응답에 id 와 이름을 같이 보낸다.
 * 예) destinationDongId 105 → "죽전1동", pickupPointId 11 → "단국대 정문 거점"
 * 목록 응답은 같은 이름을 여러 번 쓰므로 id 를 모아 한 번에 읽는다.
 */
@Component
@RequiredArgsConstructor
public class PlaceNameReader {

    private final UniversityRepository universityRepository;
    private final PickupPointRepository pickupPointRepository;
    private final AdministrativeAreaRepository administrativeAreaRepository;

    /** 학교 id → 학교 이름. */
    public Map<Long, String> universityNames(Collection<Long> ids) {
        return universityRepository.findAllById(withoutNull(ids)).stream()
                .collect(Collectors.toMap(University::getId, University::getName));
    }

    /** 거점 id → 거점 이름. */
    public Map<Long, String> pickupPointNames(Collection<Long> ids) {
        return pickupPointRepository.findAllById(withoutNull(ids)).stream()
                .collect(Collectors.toMap(PickupPoint::getId, PickupPoint::getName));
    }

    /** 행정구역(시·구·동) id → 이름. */
    public Map<Long, String> areaNames(Collection<Long> ids) {
        return administrativeAreaRepository.findAllById(withoutNull(ids)).stream()
                .collect(Collectors.toMap(AdministrativeArea::getId, AdministrativeArea::getName));
    }

    /** 공용 거점처럼 id 가 비어 있는 값은 빼고, 같은 id 는 한 번만 찾는다. */
    private static List<Long> withoutNull(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
