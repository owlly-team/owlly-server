package com.yongyong.owlly.domain.user.repository;

import com.yongyong.owlly.domain.user.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {}
