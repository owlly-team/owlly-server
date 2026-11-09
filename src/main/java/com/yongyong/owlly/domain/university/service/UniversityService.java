package com.yongyong.owlly.domain.university.service;

import com.yongyong.owlly.domain.university.dto.UniversityResponse;
import com.yongyong.owlly.domain.university.SupportedUniversities;
import com.yongyong.owlly.domain.university.entity.University;
import com.yongyong.owlly.domain.university.entity.UniversityDomain;
import com.yongyong.owlly.domain.university.repository.UniversityDomainRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityService {
    private final UniversityRepository universities;
    private final UniversityDomainRepository domains;

    public List<UniversityResponse> getActiveUniversities() {
        List<University> active = universities.findByActiveTrueAndNameInOrderByIdAsc(SupportedUniversities.NAMES);
        if (active.isEmpty()) return List.of();
        Map<Long, List<String>> byUniversity = domains
                .findByActiveTrueAndUniversityIdInOrderByDomainAsc(active.stream().map(University::getId).toList())
                .stream().collect(Collectors.groupingBy(UniversityDomain::getUniversityId,
                        Collectors.mapping(UniversityDomain::getDomain, Collectors.toList())));
        return active.stream().map(u -> new UniversityResponse(u.getId(), u.getName(),
                byUniversity.getOrDefault(u.getId(), List.of()).stream()
                        .filter(domain -> domain.equals(SupportedUniversities.EMAIL_DOMAINS.get(u.getName())))
                        .toList())).toList();
    }
}
