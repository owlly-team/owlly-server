package com.yongyong.owlly.domain.region.service;

import com.yongyong.owlly.domain.region.entity.AdministrativeArea;

/**
 * 가입에서 필요한 하차 좌표 판별 계약.
 * 공통 지역 서비스가 준비되면 이 인터페이스를 구현한 어댑터 Bean으로 연결한다.
 * 결과는 DB에 존재하는 행정동(DONG)이어야 하며 임의 ID를 만들지 않는다.
 */
public interface DropoffLocationService {
    AdministrativeArea resolveDong(double latitude, double longitude);
}
