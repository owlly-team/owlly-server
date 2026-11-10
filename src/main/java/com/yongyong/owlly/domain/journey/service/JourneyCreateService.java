package com.yongyong.owlly.domain.journey.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.JourneyCreate;
import com.yongyong.owlly.domain.journey.dto.JourneyResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.entity.PartyStatus;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 귀가 생성 (API-15).
 *
 * <p>상황: 학생이 거점을 고르고 하차 핀을 찍은 뒤 "막차로 갈게요", "혼자 택시로 갈게요" 또는 동승 등록을 하면,
 * 그 귀가 한 건이 먼저 만들어진다. 이후 선택 기록(API-17)과 동승 신청(API-19)이 이 귀가에 붙는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class JourneyCreateService {

    /** 이미 끝난 팟. 이 팟의 MATCHED 신청은 진행 중인 동승으로 보지 않는다. */
    private static final List<PartyStatus> ENDED_PARTY_STATUSES = List.of(PartyStatus.COMPLETED, PartyStatus.CANCELLED);

    private final UserRepository userRepository;
    private final PickupPointRepository pickupPointRepository;
    private final JourneyRepository journeyRepository;
    private final RideRequestRepository rideRequestRepository;
    private final DropoffLocationService dropoffLocations;
    private final JourneyService journeyService;

    /**
     * 귀가 한 건을 만든다. 결과는 UNRESOLVED 로 시작한다.
     * - 거점이 없거나, 쓸 수 없거나(비활성), 내 학교 전용이 아닌 다른 학교 거점이면 404 RESOURCE_NOT_FOUND
     *   (해당 사용자에게 공개되지 않는 자원은 없는 것과 똑같이 응답한다, docs/api.md 6절).
     * - 하차 핀은 행정동으로 바꿔 동만 저장한다(좌표는 저장하지 않음). 판별 못 하면 422 LOCATION_UNRESOLVABLE.
     * - 같은 학생·같은 clientJourneyKey·같은 요청이면 먼저 만든 귀가를 그대로 돌려준다(응답을 못 받아 재시도한 경우).
     *   같은 키로 다른 요청이면 409 IDEMPOTENCY_CONFLICT.
     * - 다른 귀가에 진행 중인 동승(대기 중이거나 아직 끝나지 않은 팟)이 있으면 409 ACTIVE_JOURNEY_CONFLICT.
     * 학교는 로그인한 학생의 학교를 그대로 적어 둔다(귀가 당시 학교 스냅샷).
     */
    public JourneyResponse create(Long userId, JourneyCreate request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        PickupPoint pickupPoint = pickupPointRepository.findById(request.pickupPointId())
                .filter(p -> p.isActive() && (p.getUniversityId() == null || p.getUniversityId().equals(user.getUniversityId())))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        AdministrativeArea dong = dropoffLocations.resolveDong(request.dropoff().latitude(), request.dropoff().longitude());
        // DB 가 시각을 마이크로초까지만 저장하므로, 저장할 때와 비교할 때 같은 정밀도로 맞춘다.
        var departureAt = request.intendedDepartureAt().truncatedTo(ChronoUnit.MICROS);

        // 같은 학생의 귀가 생성은 한 번에 하나씩: 아래 확인과 저장이 다른 요청과 겹치지 않게 한다.
        journeyRepository.lockUser(userId);

        var existing = journeyRepository.findByClientJourneyKey(request.clientJourneyKey());
        if (existing.isPresent()) {
            Journey journey = existing.get();
            boolean sameRequest = journey.getUserId().equals(userId)
                    && journey.getPickupPointId().equals(pickupPoint.getId())
                    && journey.getDestinationDongId().equals(dong.getId())
                    && journey.getIntendedDepartureAt().equals(departureAt);
            if (!sameRequest) {
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return journeyService.toResponse(journey, null);
        }

        if (rideRequestRepository.existsActiveByUserId(userId, ENDED_PARTY_STATUSES)) {
            throw new OwllyException(ErrorCode.ACTIVE_JOURNEY_CONFLICT);
        }

        Journey journey = journeyRepository.save(Journey.builder()
                .clientJourneyKey(request.clientJourneyKey())
                .userId(userId)
                .universityId(user.getUniversityId())
                .pickupPointId(pickupPoint.getId())
                .destinationDongId(dong.getId())
                .intendedDepartureAt(departureAt)
                .outcome(JourneyOutcome.UNRESOLVED)
                .build());
        return journeyService.toResponse(journey, null);
    }
}
