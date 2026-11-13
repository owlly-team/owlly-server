package com.yongyong.owlly.domain.region;

import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.university.entity.University;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 서버가 켜질 때 학교별 출발 거점(정문)을 채운다.
 * 예) 강남대 학생이 "어디서 출발할까요?"를 열면 "강남대 정문"이 목록에 나온다.
 *
 * <p>이미 같은 학교·같은 이름의 거점이 있으면 건드리지 않는다(좌표를 직접 고쳐 둔 값을 덮어쓰지 않는다).
 * 거점의 행정구역은 좌표로 카카오에 물어 정한다. 카카오가 안 되면 그 거점만 건너뛰고 서버는 정상 기동하며,
 * 다음에 서버를 켤 때 다시 시도한다. 학교 목록은 {@link com.yongyong.owlly.domain.university.UniversitySeed}가 먼저 채운다.
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class PickupPointSeed implements ApplicationRunner {

    /** 시드할 거점 한 곳. 좌표는 지도에서 확인한 정문 위치다. */
    record Seed(String universityName, String name, String latitude, String longitude, String meetingNote) {}

    static final String GATE_NOTE = "정문 앞 큰길 쪽";

    static final List<Seed> SEEDS = List.of(
            new Seed("강남대학교", "강남대 정문", "37.2731000", "127.1290200", GATE_NOTE),
            new Seed("경희대학교", "경희대 정문", "37.2473596", "127.0784220", GATE_NOTE),
            new Seed("단국대학교", "단국대 정문", "37.3234613", "127.1255060", GATE_NOTE),
            new Seed("명지대학교", "명지대 정문", "37.2247268", "127.1877924", GATE_NOTE),
            new Seed("한국외국어대학교", "한국외대 정문", "37.3356549", "127.2553991", GATE_NOTE));

    private final UniversityRepository universityRepository;
    private final PickupPointRepository pickupPointRepository;
    private final DropoffLocationService areaResolver;

    @Override
    public void run(ApplicationArguments args) {
        for (Seed seed : SEEDS) {
            try {
                seedOne(seed);
            } catch (RuntimeException e) {
                log.warn("거점 시드를 건너뜁니다: {} ({})", seed.name(), e.getMessage());
            }
        }
    }

    /** 학교가 없거나 같은 거점이 이미 있으면 아무것도 하지 않는다. 행정구역 판별에 실패하면 예외가 그대로 올라간다. */
    void seedOne(Seed seed) {
        University university = universityRepository.findByName(seed.universityName()).orElse(null);
        if (university == null) {
            log.warn("거점 시드: 학교가 없어 건너뜁니다: {}", seed.universityName());
            return;
        }
        if (pickupPointRepository.existsByUniversityIdAndName(university.getId(), seed.name())) {
            return;
        }
        double latitude = Double.parseDouble(seed.latitude());
        double longitude = Double.parseDouble(seed.longitude());
        AdministrativeArea dong;
        try {
            dong = areaResolver.resolveDong(latitude, longitude);
        } catch (OwllyException e) {
            throw new IllegalStateException("행정구역을 판별하지 못했습니다: " + e.getErrorCode(), e);
        }
        pickupPointRepository.save(PickupPoint.builder()
                .universityId(university.getId())
                .name(seed.name())
                .areaId(dong.getId())
                .latitude(new BigDecimal(seed.latitude()))
                .longitude(new BigDecimal(seed.longitude()))
                .meetingNote(seed.meetingNote())
                .active(true)
                .build());
        log.info("거점 시드: {} 를 추가했습니다.", seed.name());
    }
}
