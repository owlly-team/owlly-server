package com.yongyong.owlly.domain.region.repository;

import com.yongyong.owlly.domain.region.entity.PickupPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 고정 출발 거점 조회 (API-04 GET /pickup-points).
 *
 * <p>학생이 홈에서 귀가 방법을 비교하거나 동승을 등록할 때(S-01, S-02 화면)
 * "어디서 출발할지" 고르는 후보 목록으로 쓰인다.
 * 점검 등으로 쓸 수 없는 거점은 is_active=false 로 두어 학생에게 보이지 않게 한다.
 */
public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    /** 활성 거점 전체. 학교 구분 없이 요청한 경우. */
    List<PickupPoint> findByActiveTrueOrderByIdAsc();

    /**
     * 활성 거점 중 내 학교 전용 거점 + 공용 거점(university_id 가 NULL).
     * 예: 단국대 학생이면 "단국대 정문"(학교 전용) + 여러 학교가 같이 쓰는 "죽전역"(공용).
     */
    @Query("""
            select p from PickupPoint p
            where p.active = true
              and (p.universityId is null or p.universityId = :universityId)
            order by p.id asc
            """)
    List<PickupPoint> findActiveByUniversityIdOrCommon(@Param("universityId") Long universityId);

    /** 같은 학교에 같은 이름의 거점이 이미 있는지(활성 여부와 관계없이). 거점 시드가 중복 생성을 피할 때 쓴다. */
    boolean existsByUniversityIdAndName(Long universityId, String name);
}
