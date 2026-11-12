package com.yongyong.owlly.domain.demand.dto;

import com.yongyong.owlly.domain.demand.entity.DemandEstimate;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * 노선 후보(수요 셀) 한 건. 수요 지도 목록(API-43)과 후보 상세(API-44)의 응답 단위.
 *
 * <p>추정 수요·후보 순위·근거 지표는 모두 AI 수요 산출(run)의 결과이고, 서버는 저장된 값을 읽어 보여 준다.
 * slotStart 는 HH:mm 으로 내보낸다. estimatedWeeklyDemand·candidateThreshold·candidateRank 는 미산출이면 null.
 */
public record DemandCandidate(
        String id,
        String runId,
        String universityId,
        String pickupPointId,
        String pickupPointName,
        String destinationDongId,
        String destinationDongName,
        int weekday,
        String slotStart,
        int journeyCount,
        int failedJourneyCount,
        int pooledJourneyCount,
        int busSelectedCount,
        int soloSelectedCount,
        BigDecimal estimatedWeeklyDemand,
        Integer candidateRank,
        BigDecimal candidateThreshold,
        List<Evidence> evidence) {

    private static final java.time.format.DateTimeFormatter HHMM =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    /** 엔티티 + 조인한 이름 + 파싱한 근거 지표로 응답을 조립한다. */
    public static DemandCandidate of(
            DemandEstimate e, String pickupPointName, String destinationDongName, List<Evidence> evidence) {
        return new DemandCandidate(
                String.valueOf(e.getId()),
                String.valueOf(e.getRunId()),
                String.valueOf(e.getUniversityId()),
                String.valueOf(e.getPickupPointId()),
                pickupPointName,
                String.valueOf(e.getDestinationDongId()),
                destinationDongName,
                e.getWeekday(),
                formatSlot(e.getSlotStart()),
                e.getJourneyCount(),
                e.getFailedJourneyCount(),
                e.getPooledJourneyCount(),
                e.getBusSelectedCount(),
                e.getSoloSelectedCount(),
                e.getEstimatedWeeklyDemand(),
                e.getCandidateRank(),
                e.getCandidateThreshold(),
                evidence);
    }

    private static String formatSlot(LocalTime slotStart) {
        return slotStart == null ? null : slotStart.format(HHMM);
    }
}
