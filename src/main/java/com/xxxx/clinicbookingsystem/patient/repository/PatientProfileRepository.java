package com.xxxx.clinicbookingsystem.patient.repository;

import com.xxxx.clinicbookingsystem.patient.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    Optional<PatientProfile> findByAccount_Id(Long accountId);
}
