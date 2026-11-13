package com.yongyong.owlly.domain.transit.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 막차를 안내하는 구간 목록: 거점 이름과 도착 구가 모두 맞을 때만 안내한다. */
class TransitPlanCatalogTest {

    private final TransitPlanCatalog catalog = new TransitPlanCatalog("단국대 정문");

    @Test
    void 단국대_정문에서_영통구로_가면_720_3번을_안내한다() {
        assertThat(catalog.find("단국대 정문", TransitPlanCatalog.YEONGTONG_DISTRICT_CODE))
                .get().extracting(TransitPlanCatalog.TransitPlan::routeName).isEqualTo("720-3");
    }

    @Test
    void 강남대_정문에서_수지구로_가면_33번을_안내한다() {
        assertThat(catalog.find("강남대 정문", TransitPlanCatalog.SUJI_DISTRICT_CODE))
                .get().extracting(TransitPlanCatalog.TransitPlan::routeName).isEqualTo("33");
    }

    @Test
    void 거점_이름이나_도착_구가_다르면_안내하지_않는다() {
        assertThat(catalog.find("강남대 정문", TransitPlanCatalog.YEONGTONG_DISTRICT_CODE)).isEmpty();
        assertThat(catalog.find("단국대 정문", TransitPlanCatalog.SUJI_DISTRICT_CODE)).isEmpty();
        assertThat(catalog.find("경희대 정문", TransitPlanCatalog.SUJI_DISTRICT_CODE)).isEmpty();
        assertThat(catalog.find(null, TransitPlanCatalog.SUJI_DISTRICT_CODE)).isEmpty();
    }
}
