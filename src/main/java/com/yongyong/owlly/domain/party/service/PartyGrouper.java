package com.yongyong.owlly.domain.party.service;

import com.yongyong.owlly.domain.party.dto.MatchingCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 같은 그룹의 대기 신청을 2~4명 팟으로 나누는 규칙 (ERD 6절 "매칭과 동승").
 *
 * <ul>
 *   <li>먼저 신청한 순서대로 앞 팟부터 채운다. 한 팟은 최대 4명.</li>
 *   <li>같은 팟 사람과 성별 조건이 맞지 않거나 서로 차단한 사이면 다음 팟으로 넘긴다.</li>
 *   <li>혼자 남은 사람이 있으면 3명 이상인 팟에서 늦게 들어온 사람을 옮겨 2명을 만든다.
 *       예) 5명이면 4+1 이 아니라 3+2.</li>
 *   <li>그래도 혼자면 매칭 실패.</li>
 * </ul>
 */
public final class PartyGrouper {

    static final int MIN_PARTY_SIZE = 2;
    static final int MAX_PARTY_SIZE = 4;

    private PartyGrouper() {
    }

    /**
     * @param candidates   먼저 신청한 순서(created_at, id)로 정렬된 신청
     * @param blockedPairs 서로 차단한 학생 쌍
     */
    public static Result group(List<MatchingCandidate> candidates, Set<UserPair> blockedPairs) {
        List<List<MatchingCandidate>> parties = new ArrayList<>();
        for (MatchingCandidate candidate : candidates) {
            List<MatchingCandidate> target = parties.stream()
                    .filter(party -> party.size() < MAX_PARTY_SIZE)
                    .filter(party -> canJoin(candidate, party, blockedPairs))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                target = new ArrayList<>();
                parties.add(target);
            }
            target.add(candidate);
        }

        for (List<MatchingCandidate> single : parties) {
            if (single.size() == 1) {
                borrowOneMember(single, parties, blockedPairs);
            }
        }

        List<List<MatchingCandidate>> matched = new ArrayList<>();
        List<MatchingCandidate> unmatched = new ArrayList<>();
        for (List<MatchingCandidate> party : parties) {
            if (party.size() >= MIN_PARTY_SIZE) {
                matched.add(List.copyOf(party));
            } else {
                unmatched.addAll(party);
            }
        }
        return new Result(matched, unmatched);
    }

    /** 혼자인 사람에게 3명 이상인 팟에서 늦게 들어온 사람 한 명을 옮겨 준다. */
    private static void borrowOneMember(List<MatchingCandidate> single,
                                        List<List<MatchingCandidate>> parties,
                                        Set<UserPair> blockedPairs) {
        MatchingCandidate alone = single.get(0);
        for (List<MatchingCandidate> donor : parties) {
            if (donor == single || donor.size() <= MIN_PARTY_SIZE) {
                continue;
            }
            for (int i = donor.size() - 1; i >= 0; i--) {
                MatchingCandidate moving = donor.get(i);
                if (canPair(alone, moving, blockedPairs)) {
                    donor.remove(i);
                    single.add(moving);
                    return;
                }
            }
        }
    }

    private static boolean canJoin(MatchingCandidate candidate, List<MatchingCandidate> party, Set<UserPair> blockedPairs) {
        return party.stream().allMatch(member -> canPair(candidate, member, blockedPairs));
    }

    private static boolean canPair(MatchingCandidate a, MatchingCandidate b, Set<UserPair> blockedPairs) {
        return !a.userId().equals(b.userId())
                && a.genderCompatibleWith(b)
                && !blockedPairs.contains(UserPair.of(a.userId(), b.userId()));
    }

    /** 나눈 결과. matched 의 각 팟도 먼저 신청한 순서를 유지한다. */
    public record Result(
            List<List<MatchingCandidate>> parties,  // 2~4명 팟들
            List<MatchingCandidate> unmatched       // 혼자 남아 매칭 실패한 신청
    ) {}

    /** 두 학생 쌍. user_blocks 처럼 작은 id 를 앞에 둬서 순서와 상관없이 같은 쌍으로 본다. */
    public record UserPair(
            long lowUserId,   // 작은 사용자 id
            long highUserId   // 큰 사용자 id
    ) {
        public static UserPair of(long a, long b) {
            return new UserPair(Math.min(a, b), Math.max(a, b));
        }
    }
}
