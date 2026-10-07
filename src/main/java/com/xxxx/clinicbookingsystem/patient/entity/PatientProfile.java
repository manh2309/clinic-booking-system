package com.xxxx.clinicbookingsystem.patient.entity;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "patient_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientProfile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long patientProfileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "address", length = 500)
    private String address;
}
