package com.yongyong.owlly.domain.region.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.region.client.KakaoLocalClient;
import com.yongyong.owlly.domain.region.client.KakaoLocalClient.KakaoLocalException;
import com.yongyong.owlly.domain.region.client.KakaoLocalClient.KakaoRegion;
import com.yongyong.owlly.domain.region.dto.ResolvedArea;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하차 좌표 → 행정동·매칭 권역 판별 (공통).
 *
 * <p>쓰는 곳: 회원가입(기본 하차 지점), 막차·동승·혼자 택시 비교, 귀가 생성, 동승 신청.
 * 카카오에 좌표를 물어 행정동을 받고, administrative_areas 의 같은 공식 코드 줄의 id 를 돌려준다.
 * 처음 보는 시·구·동이면 그 자리에서 추가한다(행정구역 시드를 따로 넣지 않는다).
 *
 * <p>계층 규칙 (카카오 실제 응답 기준)
 * <ul>
 *   <li>특별시·광역시: "서울특별시 / 서초구 / 서초2동" → 시 = 서울특별시, 구 = 서초구</li>
 *   <li>일반구가 있는 시: "경기도 / 수원시 영통구 / 영통3동" → 시 = 수원시, 구 = 영통구</li>
 *   <li>구가 없는 시·군: "경기도 / 오산시 / 중앙동" → 시 = 오산시, 매칭 권역도 오산시</li>
 * </ul>
 * 시·구 공식 코드는 행정동 코드 앞자리로 만든다. 예) 4111758500 → 구 4111700000, 시 4111000000
 */
@Service
@RequiredArgsConstructor
public class AdministrativeAreaResolver {

    private final KakaoLocalClient kakaoLocalClient;
    private final AdministrativeAreaRepository administrativeAreaRepository;

    /**
     * 좌표가 속한 행정동과 매칭 권역(구, 구가 없으면 시).
     * - 바다·국외처럼 행정동이 없으면 422 LOCATION_UNRESOLVABLE
     * - 카카오를 쓸 수 없으면 503 UPSTREAM_UNAVAILABLE
     */
    @Transactional
    public ResolvedArea resolve(double latitude, double longitude) {
        KakaoRegion region;
        try {
            region = kakaoLocalClient.findAdministrativeDong(latitude, longitude)
                    .orElseThrow(() -> new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE));
        } catch (KakaoLocalException e) {
            throw new OwllyException(ErrorCode.UPSTREAM_UNAVAILABLE);
        }
        Hierarchy hierarchy = toHierarchy(region)
                .orElseThrow(() -> new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE));

        AdministrativeArea city = findOrCreate(null, hierarchy.city());
        AdministrativeArea district = (hierarchy.district() == null)
                ? city
                : findOrCreate(city.getId(), hierarchy.district());
        AdministrativeArea dong = findOrCreate(district.getId(), hierarchy.dong());
        return new ResolvedArea(dong.getId(), district.getId());
    }

    /**
     * 카카오 행정동 한 건을 시·구·동으로 나눈다. 이름이 비었거나 코드가 10자리 숫자가 아니면 빈 값.
     * district 가 null 이면 구가 없는 시(군)라서 시가 매칭 권역이다.
     */
    static Optional<Hierarchy> toHierarchy(KakaoRegion region) {
        String code = region.code();
        String depth1 = region.depth1Name().trim();
        String depth2 = region.depth2Name().trim();
        String depth3 = region.depth3Name().trim();
        if (code == null || !code.matches("\\d{10}") || depth1.isEmpty() || depth3.isEmpty()) {
            return Optional.empty();
        }
        Area dong = new Area(AreaLevel.DONG, code, depth3);

        if (isMetropolitan(depth1)) {
            Area city = new Area(AreaLevel.CITY, code.substring(0, 2) + "00000000", depth1);
            Area district = depth2.isEmpty()
                    ? null
                    : new Area(AreaLevel.DISTRICT, code.substring(0, 5) + "00000", depth2);
            return Optional.of(new Hierarchy(city, district, dong));
        }
        if (depth2.isEmpty()) {
            return Optional.empty();
        }
        int space = depth2.indexOf(' ');
        if (space > 0) {
            Area city = new Area(AreaLevel.CITY, code.substring(0, 4) + "000000", depth2.substring(0, space));
            Area district = new Area(AreaLevel.DISTRICT, code.substring(0, 5) + "00000", depth2.substring(space + 1).trim());
            return Optional.of(new Hierarchy(city, district, dong));
        }
        Area city = new Area(AreaLevel.CITY, code.substring(0, 5) + "00000", depth2);
        return Optional.of(new Hierarchy(city, null, dong));
    }

    /** 특별시·광역시·특별자치시: 1단계 이름이 시 자체다. */
    private static boolean isMetropolitan(String depth1) {
        return depth1.endsWith("특별시") || depth1.endsWith("광역시") || depth1.endsWith("특별자치시");
    }

    /** 공식 코드로 찾고, 없으면 추가한 뒤 다시 찾는다(동시에 추가돼도 한 줄만 남는다). */
    private AdministrativeArea findOrCreate(Long parentId, Area area) {
        return administrativeAreaRepository.findByOfficialCode(area.officialCode())
                .orElseGet(() -> {
                    administrativeAreaRepository.insertIfAbsent(parentId, area.officialCode(), area.name(), area.level().name());
                    return administrativeAreaRepository.findByOfficialCode(area.officialCode()).orElseThrow();
                });
    }

    /** 시·구·동 세 단계. district 가 null 이면 구가 없는 시(군). */
    record Hierarchy(
            Area city,       // 시 (특별시·광역시 포함)
            Area district,   // 구. 구가 없는 시·군이면 null
            Area dong        // 행정동
    ) {}

    /** 행정구역 한 줄로 저장할 값. */
    record Area(
            AreaLevel level,      // CITY / DISTRICT / DONG
            String officialCode,  // 10자리 공식 코드
            String name           // 이름. 예) "영통구"
    ) {}
}
