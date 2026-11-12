package com.yongyong.owlly.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 시계 외 판단: 하차 지점 중 하나라도 거점과 다른 시·군이면 true. 거점의 시·군을 모르면 false. */
class OutOfCityDetectorTest {

    private static final long YONGIN = 30L;        // 용인시
    private static final long SUWON = 20L;         // 수원시
    private static final long SUWON_YEONGTONG = 21L;   // 수원시 영통구(구가 있는 시)
    private static final long OSAN = 40L;          // 오산시(구가 없는 시)
    private static final long YONGIN_SUJI_DONG = 33L;  // 용인시 안의 동

    private final AdministrativeAreaRepository repository = mock(AdministrativeAreaRepository.class);
    private final OutOfCityDetector detector = new OutOfCityDetector(repository);

    private void area(long id, Long parentId, AreaLevel level) {
        when(repository.findById(id)).thenReturn(Optional.of(
                AdministrativeArea.builder().id(id).parentId(parentId).level(level).name("지역" + id).build()));
    }

    private void hierarchy() {
        area(YONGIN, null, AreaLevel.CITY);
        area(SUWON, null, AreaLevel.CITY);
        area(SUWON_YEONGTONG, SUWON, AreaLevel.DISTRICT);
        area(OSAN, null, AreaLevel.CITY);
        area(YONGIN_SUJI_DONG, YONGIN, AreaLevel.DONG);
    }

    @Test
    void 다른_시의_구에_내리면_시계_외() {
        hierarchy();

        assertThat(detector.isOutOfCity(YONGIN, List.of(SUWON_YEONGTONG))).isTrue();
    }

    @Test
    void 같은_시_안이면_구_동_어느_단계든_시계_외가_아니다() {
        hierarchy();

        assertThat(detector.isOutOfCity(YONGIN, List.of(YONGIN_SUJI_DONG))).isFalse();
        assertThat(detector.isOutOfCity(YONGIN, List.of(YONGIN))).isFalse();
    }

    @Test
    void 구가_없는_시도_그_시_자체로_비교한다() {
        hierarchy();

        assertThat(detector.isOutOfCity(YONGIN, List.of(OSAN))).isTrue();
    }

    @Test
    void 팟처럼_하차_지점이_여럿이면_하나라도_다른_시일_때_시계_외() {
        hierarchy();

        assertThat(detector.isOutOfCity(YONGIN, List.of(YONGIN_SUJI_DONG, SUWON_YEONGTONG))).isTrue();
        assertThat(detector.isOutOfCity(YONGIN, List.of(YONGIN_SUJI_DONG, YONGIN))).isFalse();
    }

    @Test
    void 거점의_행정구역을_모르면_할증을_붙이지_않는다() {
        hierarchy();
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(detector.isOutOfCity(99L, List.of(SUWON_YEONGTONG))).isFalse();
        assertThat(detector.isOutOfCity(null, List.of(SUWON_YEONGTONG))).isFalse();
    }

    @Test
    void 하차_지점의_시를_모르면_그_지점은_건너뛴다() {
        hierarchy();
        when(repository.findById(98L)).thenReturn(Optional.empty());

        assertThat(detector.isOutOfCity(YONGIN, List.of(98L))).isFalse();
        assertThat(detector.isOutOfCity(YONGIN, List.of(98L, SUWON_YEONGTONG))).isTrue();
    }
}
