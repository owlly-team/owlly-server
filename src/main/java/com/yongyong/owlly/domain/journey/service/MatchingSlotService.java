package com.yongyong.owlly.domain.journey.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.journey.dto.MatchingSlot;
import com.yongyong.owlly.domain.journey.dto.WaitingRequestRow;
import com.yongyong.owlly.domain.journey.repository.RideRequestRepository;
import com.yongyong.owlly.domain.party.dto.MatchingCandidate;
import com.yongyong.owlly.domain.party.repository.RidePartyRepository;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.repository.AdministrativeAreaRepository;
import com.yongyong.owlly.domain.region.repository.PickupPointRepository;
import com.yongyong.owlly.domain.user.entity.Role;
import com.yongyong.owlly.domain.user.entity.StudentProfile;
import com.yongyong.owlly.domain.user.entity.User;
import com.yongyong.owlly.domain.user.repository.StudentProfileRepository;
import com.yongyong.owlly.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출발 시간대별 대기 인원 (API-18).
 *
 * <p>상황: 학생이 동승을 등록하려고 출발 시간을 고를 때, 시간 칩마다 "지금 몇 명이 기다리는지"를 본다
 * (화면정의서 S-03 출발 시간 선택, 매칭 실패 후 다음 시간대 안내 S-04).
 * 같은 팟에 들어갈 수 있는 신청만 센다: 같은 학교·거점·도착 구, 성별 조건이 맞고, 서로 차단한 사이가 아닌 신청.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingSlotService {

    /** 출발 15분 전이 지나 등록할 수 없는 시간대. */
    static final String REASON_REGISTRATION_CLOSED = "REGISTRATION_CLOSED";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PickupPointRepository pickupPointRepository;
    private final AdministrativeAreaRepository administrativeAreaRepository;
    private final RideRequestRepository rideRequestRepository;
    private final RidePartyRepository ridePartyRepository;
    private final DepartureSlotPolicy slotPolicy;

    /**
     * 그날(한국 날짜)의 출발 시간대와 시간대별 대기 인원.
     * - 거점이 없거나 내 학교가 쓸 수 없으면, 도착 동이 없거나 동이 아니면 404 RESOURCE_NOT_FOUND.
     * - 학생이 아니면 403 FORBIDDEN.
     * - 대기 인원에서 내 신청은 뺀다. 내 성별 조건·상대 성별 조건이 맞지 않거나 나와 차단한 사이면 세지 않는다(0명이어도 그대로 0).
     * - 출발 15분 전이 지난 시간대도 목록에는 있고 registrationAllowed 만 false 다. 화면에서 숨길지는 화면이 정한다.
     *
     * @param sameGenderOnly 이번에 등록할 때 동성끼리만 타겠다고 고른 값
     */
    public List<MatchingSlot> getSlots(Long userId, Long pickupPointId, Long destinationDongId,
                                       LocalDate date, boolean sameGenderOnly, Instant now) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        if (user.getRole() != Role.STUDENT) {
            throw new OwllyException(ErrorCode.FORBIDDEN);
        }
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        pickupPointRepository.findById(pickupPointId)
                .filter(p -> p.isUsableBy(user.getUniversityId()))
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));
        AdministrativeArea dong = administrativeAreaRepository.findById(destinationDongId)
                .filter(area -> area.getLevel() == AreaLevel.DONG && area.getParentId() != null)
                .orElseThrow(() -> new OwllyException(ErrorCode.RESOURCE_NOT_FOUND));

        List<Instant> slots = slotPolicy.slotsOn(date);
        List<WaitingRequestRow> waiting = rideRequestRepository.findWaitingForSlots(
                user.getUniversityId(), pickupPointId, dong.getParentId(), slots);

        MatchingCandidate me = new MatchingCandidate(null, userId, profile.getGender(), sameGenderOnly);
        Set<Long> blockedWithMe = findBlockedWith(userId, waiting);

        return slots.stream()
                .map(slot -> new MatchingSlot(
                        slot,
                        (int) waiting.stream()
                                .filter(row -> row.departureAt().equals(slot))
                                .filter(row -> !row.userId().equals(userId))
                                .filter(row -> !blockedWithMe.contains(row.userId()))
                                .filter(row -> me.genderCompatibleWith(
                                        new MatchingCandidate(null, row.userId(), row.gender(), row.sameGenderOnly())))
                                .count(),
                        slotPolicy.isRegistrationOpen(slot, now),
                        slotPolicy.isRegistrationOpen(slot, now) ? null : REASON_REGISTRATION_CLOSED))
                .toList();
    }

    /** 대기 중인 학생들 가운데 나와 서로 차단한 사이인 학생(user_blocks). */
    private Set<Long> findBlockedWith(Long userId, List<WaitingRequestRow> waiting) {
        Set<Long> others = new HashSet<>();
        waiting.forEach(row -> others.add(row.userId()));
        if (others.isEmpty()) {
            return Set.of();
        }
        others.add(userId);
        Set<Long> blocked = new HashSet<>();
        ridePartyRepository.findBlockedPairs(others).forEach(pair -> {
            if (pair.getLowUserId().equals(userId)) {
                blocked.add(pair.getHighUserId());
            } else if (pair.getHighUserId().equals(userId)) {
                blocked.add(pair.getLowUserId());
            }
        });
        return blocked;
    }
}
