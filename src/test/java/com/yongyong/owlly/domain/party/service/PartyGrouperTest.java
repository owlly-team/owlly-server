package com.yongyong.owlly.domain.party.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yongyong.owlly.domain.party.dto.MatchingCandidate;
import com.yongyong.owlly.domain.party.service.PartyGrouper.Result;
import com.yongyong.owlly.domain.party.service.PartyGrouper.UserPair;
import com.yongyong.owlly.domain.user.entity.Gender;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class PartyGrouperTest {

    private static MatchingCandidate any(long id) {
        return new MatchingCandidate(id, 100 + id, Gender.MALE, false);
    }

    private static MatchingCandidate person(long id, Gender gender, boolean sameGenderOnly) {
        return new MatchingCandidate(id, 100 + id, gender, sameGenderOnly);
    }

    private static List<Long> ids(List<MatchingCandidate> party) {
        return party.stream().map(MatchingCandidate::requestId).toList();
    }

    @Test
    void 혼자면_매칭_실패() {
        Result result = PartyGrouper.group(List.of(any(1)), Set.of());

        assertThat(result.parties()).isEmpty();
        assertThat(ids(result.unmatched())).containsExactly(1L);
    }

    @Test
    void 네_명까지_한_팟_다섯_명이면_3명과_2명() {
        List<MatchingCandidate> five = LongStream.rangeClosed(1, 5).mapToObj(PartyGrouperTest::any).toList();

        Result result = PartyGrouper.group(five, Set.of());

        assertThat(result.unmatched()).isEmpty();
        assertThat(result.parties()).hasSize(2);
        assertThat(ids(result.parties().get(0))).containsExactly(1L, 2L, 3L);
        assertThat(ids(result.parties().get(1))).containsExactly(5L, 4L);
    }

    @Test
    void 여덟_명이면_4명씩() {
        List<MatchingCandidate> eight = LongStream.rangeClosed(1, 8).mapToObj(PartyGrouperTest::any).toList();

        Result result = PartyGrouper.group(eight, Set.of());

        assertThat(result.parties()).extracting(List::size).containsExactly(4, 4);
    }

    @Test
    void 한쪽이라도_동성만_원하면_성별이_같아야_한다() {
        List<MatchingCandidate> people = List.of(
                person(1, Gender.FEMALE, true),
                person(2, Gender.MALE, false),
                person(3, Gender.FEMALE, false));

        Result result = PartyGrouper.group(people, Set.of());

        assertThat(result.parties()).hasSize(1);
        assertThat(ids(result.parties().get(0))).containsExactly(1L, 3L);
        assertThat(ids(result.unmatched())).containsExactly(2L);
    }

    @Test
    void 서로_차단한_학생은_같은_팟에_넣지_않는다() {
        List<MatchingCandidate> people = List.of(any(1), any(2), any(3), any(4));
        Set<UserPair> blocked = Set.of(UserPair.of(102, 101));

        Result result = PartyGrouper.group(people, blocked);

        assertThat(result.unmatched()).isEmpty();
        assertThat(ids(result.parties().get(0))).containsExactly(1L, 3L);
        assertThat(ids(result.parties().get(1))).containsExactly(2L, 4L);
    }
}
