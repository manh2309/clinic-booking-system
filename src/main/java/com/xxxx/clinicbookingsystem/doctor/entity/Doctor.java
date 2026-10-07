package com.xxxx.clinicbookingsystem.doctor.entity;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 255)
    private String qualification;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;
}
