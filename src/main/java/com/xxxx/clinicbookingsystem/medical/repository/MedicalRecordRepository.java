package com.xxxx.clinicbookingsystem.medical.repository;

import com.xxxx.clinicbookingsystem.medical.entity.MedicalRecord;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    boolean existsByAppointmentId(Long appointmentId);

    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.slot"})
    Page<MedicalRecord> findByAppointmentPatientIdAndAppointmentStatus(
            Long patientId, AppointmentStatus status, Pageable pageable);

    @Query("SELECT r FROM MedicalRecord r JOIN FETCH r.appointment a JOIN FETCH a.doctor d JOIN FETCH a.slot " +
            "WHERE a.id = :appointmentId AND a.patient.id = :patientId AND a.status = 'COMPLETED'")
    Optional<MedicalRecord> findForPatient(Long appointmentId, Long patientId);

    @Query("SELECT r FROM MedicalRecord r JOIN FETCH r.appointment a JOIN FETCH a.doctor d JOIN FETCH a.slot " +
            "WHERE a.id = :appointmentId AND d.account.id = :accountId")
    Optional<MedicalRecord> findForDoctor(Long appointmentId, Long accountId);
}
