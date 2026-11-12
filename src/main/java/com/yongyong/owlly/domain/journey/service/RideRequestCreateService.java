package com.yongyong.owlly.domain.journey.service;

import com.yongyong.owlly.common.crypto.TextCryptor;
import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.RideRequestCreate;
import com.yongyong.owlly.domain.journey.dto.RideRequestResponse;
import com.yongyong.owlly.domain.journey.entity.Journey;
import com.yongyong.owlly.domain.journey.entity.JourneyOutcome;
import com.yongyong.owlly.domain.journey.entity.RideRequest;
import com.yongyong.owlly.domain.journey.entity.RideRequestStatus;
import com.yongyong.owlly.domain.journey.repository.JourneyRepository;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.PickupPoint;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.StudentProfile;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동승 신청·실패 후 재등록 (API-19).
 *
 * <p>상황: 학생이 출발 시간을 고르고 "{시간}으로 등록하기"를 누르면(화면정의서 S-03), 이미 만들어 둔 귀가(API-15)에
 * 동승 신청이 붙는다. 매칭에 실패해 "다음 시간대로 다시 등록"하면 같은 귀가에 새 신청이 하나 더 붙는다.
 * 신청은 매칭 작업이 같은 조건의 신청끼리 팟으로 묶는 단위다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RideRequestCreateService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PickupPointRepository pickupPointRepository;
    private final JourneyRepository journeyRepository;
    private final RideRequestRepository rideRequestRepository;
    private final DropoffLocationService dropoffLocations;
    private final TextCryptor cryptor;
    private final DepartureSlotPolicy slotPolicy;
    private final RideRequestService rideRequestService;

    /**
     * 귀가에 동승 신청을 만든다. 신청은 WAITING 으로 시작하고, 결과 안내는 출발 20분 전으로 잡는다.
     * - 로그인한 학생의 귀가가 아니거나 없으면, 거점을 쓸 수 없으면 404 RESOURCE_NOT_FOUND. 학생이 아니면 403 FORBIDDEN.
     * - 30분 단위 서비스 시간(22:00~02:00)이 아니거나 출발 15분 전이 지났으면 422 INVALID_DEPARTURE_SLOT.
     * - 하차 핀의 행정동을 판별하지 못하면 422 LOCATION_UNRESOLVABLE. 판별한 동·구를 신청에 적고, 좌표는 암호화해 저장한다.
     * - 같은 키·같은 귀가·같은 내용이면 먼저 만든 신청을 돌려준다(재시도). 같은 키의 다른 내용은 409 IDEMPOTENCY_CONFLICT.
     * - 귀가의 결과가 이미 정해졌으면(UNRESOLVED 가 아니면) 409 JOURNEY_ALREADY_RESOLVED.
     * - 이 학생에게 진행 중인 동승이 이미 있으면(어느 귀가든) 409 ACTIVE_REQUEST_EXISTS.
     * 성별은 가입한 프로필의 값을 신청에 스냅샷으로 남기고, 귀가의 거점·동·예정 시각은 이번 신청의 값으로 바꾼다.
     */
    public RideRequestResponse create(Long userId, Long journeyId, RideRequestCreate request, Instant now) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (user.getRole() != Role.STUDENT) {
            throw new OwllyException(ErrorCode.FORBIDDEN);
        }
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant departureAt = request.departureAt().truncatedTo(ChronoUnit.MICROS);
        if (!slotPolicy.isAllowedSlot(departureAt)) {
            throw new OwllyException(ErrorCode.INVALID_DEPARTURE_SLOT);
        }
        PickupPoint pickupPoint = pickupPointRepository.findById(request.pickupPointId())
                .filter(p -> p.isUsableBy(user.getUniversityId()))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        AdministrativeArea dong = dropoffLocations.resolveDong(request.dropoff().latitude(), request.dropoff().longitude());

        // 같은 학생의 신청 생성은 한 번에 하나씩: 아래 확인과 저장이 다른 요청과 겹치지 않게 한다.
        journeyRepository.lockUser(userId);
        Journey journey = journeyRepository.findByIdForUpdate(journeyId)
                .filter(j -> j.getUserId().equals(userId))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        var existing = rideRequestRepository.findByClientRequestKey(request.clientRequestKey());
        if (existing.isPresent()) {
            RideRequest previous = existing.get();
            boolean sameRequest = previous.getJourneyId().equals(journeyId)
                    && previous.getPickupPointId().equals(pickupPoint.getId())
                    && previous.getDestinationDongId().equals(dong.getId())
                    && previous.getDepartureAt().equals(departureAt)
                    && previous.isSameGenderOnly() == request.sameGenderOnly();
            if (!sameRequest) {
                throw new OwllyException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return rideRequestService.getMyRideRequest(userId, previous.getId());
        }

        if (journey.getOutcome() != JourneyOutcome.UNRESOLVED) {
            throw new OwllyException(ErrorCode.JOURNEY_ALREADY_RESOLVED);
        }
        if (!slotPolicy.isRegistrationOpen(departureAt, now)) {
            throw new OwllyException(ErrorCode.INVALID_DEPARTURE_SLOT);
        }
        if (rideRequestRepository.existsActiveByUserId(userId, JourneyCreateService.ENDED_PARTY_STATUSES)) {
            throw new OwllyException(ErrorCode.ACTIVE_REQUEST_EXISTS);
        }

        RideRequest saved = rideRequestRepository.save(RideRequest.builder()
                .clientRequestKey(request.clientRequestKey())
                .journeyId(journey.getId())
                .universityId(journey.getUniversityId())
                .pickupPointId(pickupPoint.getId())
                .districtId(dong.getParentId())
                .destinationDongId(dong.getId())
                .dropoffLatEncrypted(cryptor.encrypt(Double.toString(request.dropoff().latitude())))
                .dropoffLngEncrypted(cryptor.encrypt(Double.toString(request.dropoff().longitude())))
                .departureAt(departureAt)
                .sameGenderOnly(request.sameGenderOnly())
                .genderSnapshot(profile.getGender())
                .status(RideRequestStatus.WAITING)
                .resultDueAt(slotPolicy.resultDueAt(departureAt))
                .build());
        journey.updateIntent(pickupPoint.getId(), dong.getId(), departureAt);
        return rideRequestService.toResponse(saved, null);
    }
}
