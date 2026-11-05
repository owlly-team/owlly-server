package com.yongyong.owlly.domain.university.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대학 이메일 허용 도메인. 학교별 여러 도메인 허용. */
@Entity
@Getter
@Table(name = "university_domains")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniversityDomain extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> universities.id */
    @Column(name = "university_id", nullable = false)
    private Long universityId;

    /** 소문자 정규화 도메인. */
    @Column(nullable = false, unique = true, length = 255)
    private String domain;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
