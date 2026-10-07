package com.xxxx.clinicbookingsystem.doctor.repository;

import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    @Query(value = "SELECT d FROM Doctor d JOIN FETCH d.specialty WHERE d.active = true " +
            "AND (:specialtyId IS NULL OR d.specialty.id = :specialtyId) " +
            "AND (:keyword IS NULL OR LOWER(d.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery = "SELECT COUNT(d) FROM Doctor d WHERE d.active = true " +
                    "AND (:specialtyId IS NULL OR d.specialty.id = :specialtyId) " +
                    "AND (:keyword IS NULL OR LOWER(d.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Doctor> search(@Param("specialtyId") Long specialtyId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.specialty WHERE d.id = :id AND d.active = true")
    Optional<Doctor> findActiveByIdWithSpecialty(Long id);

    Optional<Doctor> findByAccountIdAndActiveTrue(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.account.id = :accountId AND d.active = true")
    Optional<Doctor> lockByAccountId(Long accountId);
}
