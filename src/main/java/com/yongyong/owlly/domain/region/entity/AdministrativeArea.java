package com.yongyong.owlly.domain.region.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 시·구·동 계층 기준정보 (self-reference). */
@Entity
@Getter
@Table(name = "administrative_areas")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdministrativeArea extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상위 행정구역 FK -> administrative_areas.id (최상위는 NULL). */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "official_code", nullable = false, unique = true, length = 20)
    private String officialCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AreaLevel level;

    /** 대표(중심) 위도. 수요 지도에 동을 한 점으로 찍을 때 쓴다. 미설정이면 NULL. */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /** 대표(중심) 경도. 수요 지도에 동을 한 점으로 찍을 때 쓴다. 미설정이면 NULL. */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
}
