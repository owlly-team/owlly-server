package com.yongyong.owlly.domain.region.entity;

import com.yongyong.owlly.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 고정 출발·집결 거점. 공용 거점은 university_id 가 NULL. */
@Entity
@Getter
@Table(name = "pickup_points")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickupPoint extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학교 전용 거점일 때 FK -> universities.id. 공용 거점은 NULL. */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 거점 행정구역 FK -> administrative_areas.id */
    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 집결 위치 안내. */
    @Column(name = "meeting_note", nullable = false, columnDefinition = "text")
    private String meetingNote;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
