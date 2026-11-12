package com.yongyong.owlly.domain.demand.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongyong.owlly.domain.demand.dto.DemandCandidate;
import com.yongyong.owlly.domain.demand.dto.Evidence;
import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 수요 셀(DemandEstimate)을 응답 DTO(DemandCandidate)로 조립한다.
 *
 * <p>거점·동(이름·좌표) 기준정보를 한 번에 조회해 붙이고, evidence(jsonb)를 Evidence[] 로 역직렬화한다.
 * 수요 조회(API-43·44)와 리포트 생성 시 후보 스냅샷 구성에서 함께 쓴다.
 */
@Component
@RequiredArgsConstructor
public class DemandCandidateAssembler {

    private final PickupPointRepository pickupPoints;
    private final AdministrativeAreaRepository administrativeAreas;
    private final ObjectMapper objectMapper;

    /** 셀 목록을 거점·동(이름·좌표)과 파싱한 근거 지표로 묶어 응답 DTO 로 만든다(기준정보는 한 번에 조회). */
    public List<DemandCandidate> assemble(List<DemandEstimate> estimates) {
        Set<Long> pickupIds = estimates.stream().map(DemandEstimate::getPickupPointId).collect(Collectors.toSet());
        Set<Long> dongIds = estimates.stream().map(DemandEstimate::getDestinationDongId).collect(Collectors.toSet());

        Map<Long, PickupPoint> pickups = pickupPoints.findAllById(pickupIds).stream()
                .collect(Collectors.toMap(PickupPoint::getId, p -> p));
        Map<Long, AdministrativeArea> dongs = administrativeAreas.findAllById(dongIds).stream()
                .collect(Collectors.toMap(AdministrativeArea::getId, a -> a));

        return estimates.stream()
                .map(e -> DemandCandidate.of(
                        e,
                        pickups.get(e.getPickupPointId()),
                        dongs.get(e.getDestinationDongId()),
                        parseEvidence(e.getEvidence())))
                .toList();
    }

    /** evidence(jsonb 배열 문자열)를 Evidence[] 로 역직렬화한다. 비었거나 깨졌으면 빈 목록. */
    private List<Evidence> parseEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Evidence>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
