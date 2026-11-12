package com.yongyong.owlly.domain.region.repository;

import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 시·구·동 행정구역 조회·추가.
 * 예) 귀가·동승 신청 응답에 하차 동·도착 구 이름을 붙일 때 id 로 이름을 찾는다.
 */
public interface AdministrativeAreaRepository extends JpaRepository<AdministrativeArea, Long> {

    /** 공식 코드로 찾기. 예) "4111758500" → 영통3동 */
    Optional<AdministrativeArea> findByOfficialCode(String officialCode);

    /**
     * 같은 공식 코드가 없을 때만 한 줄 추가한다. 이미 있으면 아무것도 하지 않는다.
     * 예) 처음 보는 동의 좌표가 들어왔을 때. 두 요청이 동시에 같은 동을 추가해도 한 줄만 남는다(official_code UNIQUE).
     */
    @Modifying
    @Query(value = """
            insert into administrative_areas (parent_id, official_code, name, level, created_at, updated_at)
            values (:parentId, :officialCode, :name, :level, now(), now())
            on conflict (official_code) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("parentId") Long parentId,
                        @Param("officialCode") String officialCode,
                        @Param("name") String name,
                        @Param("level") String level);
}
