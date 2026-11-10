package com.yongyong.owlly.domain.region.repository;

import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시·구·동 행정구역 조회.
 * 예) 귀가·동승 신청 응답에 하차 동·도착 구 이름을 붙일 때 id 로 이름을 찾는다.
 */
public interface AdministrativeAreaRepository extends JpaRepository<AdministrativeArea, Long> {
}
