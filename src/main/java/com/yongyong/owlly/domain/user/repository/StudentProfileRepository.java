package com.yongyong.owlly.domain.user.repository;

import com.yongyong.owlly.domain.user.entity.StudentProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudentProfile p where p.userId = :userId")
    Optional<StudentProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}
