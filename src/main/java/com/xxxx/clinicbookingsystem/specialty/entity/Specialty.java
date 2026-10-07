package com.xxxx.clinicbookingsystem.specialty.entity;

import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "specialties")
@Getter
@Setter
@NoArgsConstructor
public class Specialty extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specialty_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;
}
