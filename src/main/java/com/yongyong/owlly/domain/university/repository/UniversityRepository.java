package com.yongyong.owlly.domain.university.repository;

import com.yongyong.owlly.domain.university.entity.University;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {
    java.util.Optional<University> findByName(String name);

    List<University> findByActiveTrueAndNameInOrderByIdAsc(java.util.Collection<String> names);
}
