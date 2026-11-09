package com.yongyong.owlly.domain.university.repository;

import com.yongyong.owlly.domain.university.entity.UniversityDomain;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityDomainRepository extends JpaRepository<UniversityDomain, Long> {
    List<UniversityDomain> findByActiveTrueAndUniversityIdInOrderByDomainAsc(Collection<Long> universityIds);
    boolean existsByUniversityIdAndDomainAndActiveTrue(Long universityId, String domain);
}
