package com.xxxx.clinicbookingsystem.specialty.repository;

import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    List<Specialty> findAllByActiveTrueOrderByNameAsc();
    Optional<Specialty> findByIdAndActiveTrue(Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
