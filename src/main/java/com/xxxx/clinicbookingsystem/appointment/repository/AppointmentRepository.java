package com.xxxx.clinicbookingsystem.appointment.repository;

import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query(value = "SELECT a FROM Appointment a JOIN FETCH a.doctor JOIN FETCH a.slot WHERE a.patient.id = :patientId",
            countQuery = "SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId")
    Page<Appointment> findPatientAppointments(Long patientId, Pageable pageable);

    @Query(value = "SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor JOIN FETCH a.slot WHERE a.doctor.id = :doctorId",
            countQuery = "SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId")
    Page<Appointment> findDoctorAppointments(Long doctorId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor JOIN FETCH a.slot WHERE a.id = :id")
    Optional<Appointment> lockById(Long id);
}
