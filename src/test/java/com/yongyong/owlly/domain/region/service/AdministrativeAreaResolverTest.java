package com.yongyong.owlly.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.region.client.KakaoLocalClient.KakaoRegion;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.service.AdministrativeAreaResolver.Area;
import com.yongyong.owlly.domain.region.service.AdministrativeAreaResolver.Hierarchy;
import org.junit.jupiter.api.Test;

/** 카카오 좌표→행정구역 실제 응답(2026-11-10 호출) 값으로 시·구·동 나누기를 확인한다. */
class AdministrativeAreaResolverTest {

    private static Hierarchy split(String code, String depth1, String depth2, String depth3) {
        return AdministrativeAreaResolver.toHierarchy(new KakaoRegion(code, depth1, depth2, depth3)).orElseThrow();
    }

    @Test
    void 일반구가_있는_시는_시와_구를_나눈다() {
        Hierarchy h = split("4111758500", "경기도", "수원시 영통구", "영통3동");

        assertThat(h.city()).isEqualTo(new Area(AreaLevel.CITY, "4111000000", "수원시"));
        assertThat(h.district()).isEqualTo(new Area(AreaLevel.DISTRICT, "4111700000", "영통구"));
        assertThat(h.dong()).isEqualTo(new Area(AreaLevel.DONG, "4111758500", "영통3동"));
    }

    @Test
    void 특별시와_광역시는_1단계가_시다() {
        Hierarchy seoul = split("1165052000", "서울특별시", "서초구", "서초2동");
        Hierarchy incheon = split("2829055000", "인천광역시", "검단구", "오류왕길동");

        assertThat(seoul.city()).isEqualTo(new Area(AreaLevel.CITY, "1100000000", "서울특별시"));
        assertThat(seoul.district()).isEqualTo(new Area(AreaLevel.DISTRICT, "1165000000", "서초구"));
        assertThat(incheon.city().name()).isEqualTo("인천광역시");
        assertThat(incheon.district().name()).isEqualTo("검단구");
    }

    @Test
    void 구가_없는_시는_시가_매칭_권역이다() {
        Hierarchy h = split("4137051000", "경기도", "오산시", "중앙동");

        assertThat(h.city()).isEqualTo(new Area(AreaLevel.CITY, "4137000000", "오산시"));
        assertThat(h.district()).isNull();
        assertThat(h.dong().name()).isEqualTo("중앙동");
    }

    @Test
    void 바다처럼_이름이_비어_있으면_판별하지_않는다() {
        assertThat(AdministrativeAreaResolver.toHierarchy(new KakaoRegion("90009", "", "", ""))).isEmpty();
    }
}
