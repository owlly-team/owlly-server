package com.yongyong.owlly.domain.user.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생 개인정보와 기본 귀가 설정. PK = user_id (users 와 공유).
 * 실시간 현재 위치는 저장하지 않는다.
 */
@Entity
@Getter
@Table(name = "student_profiles")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile extends BaseTimeEntity {

    /** PK & FK -> users.id (학생 계정과 1:1). */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 실명 암호화. 대외 표시는 서버에서 부분 마스킹. */
    @Column(name = "real_name_encrypted", nullable = false, columnDefinition = "text")
    private String realNameEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    /** 동성만 매칭 기본값 true. */
    @Column(name = "same_gender_default", nullable = false)
    private boolean sameGenderDefault;

    /** 기본 하차 행정동 FK -> administrative_areas.id */
    @Column(name = "default_dong_id", nullable = false)
    private Long defaultDongId;

    @Column(name = "dropoff_lat_encrypted", nullable = false, columnDefinition = "text")
    private String dropoffLatEncrypted;

    @Column(name = "dropoff_lng_encrypted", nullable = false, columnDefinition = "text")
    private String dropoffLngEncrypted;
}
