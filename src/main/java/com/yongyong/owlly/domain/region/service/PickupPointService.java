package com.yongyong.owlly.domain.region.service;

import com.yongyong.owlly.domain.region.dto.PickupPointResponse;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고정 출발 거점 조회 (API-04).
 *
 * <p>상황: 단국대 학생이 늦은 밤 앱을 열고 "동승 등록"을 누르면 "어디서 출발할까요?"가 나온다.
 * 이때 화면에 보여 줄 후보 목록(단국대 정문, 죽전역 등)을 만드는 곳이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PickupPointService {

    private final PickupPointRepository pickupPointRepository;

    /**
     * 출발 후보 거점 목록을 만든다.
     * - 학교가 정해진 요청(보통): 그 학교 전용 거점 + 공용 거점.
     *   예) 단국대 학생 → "단국대 정문" + 여러 학교가 같이 쓰는 "죽전역"
     * - 학교 구분이 없는 요청: 활성 거점 전체.
     * 점검 중인 거점(is_active=false)은 어느 경우든 목록에서 빠진다.
     */
    public List<PickupPointResponse> getPickupPoints(Long universityId) {
        List<PickupPoint> points = (universityId == null)
                ? pickupPointRepository.findByActiveTrueOrderByIdAsc()
                : pickupPointRepository.findActiveByUniversityIdOrCommon(universityId);
        return points.stream().map(PickupPointResponse::from).toList();
    }
}
