package com.xxxx.clinicbookingsystem.appointment.repository;

import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("select distinct a.patient.id from Appointment a where a.doctor.id=:doctorId and a.status in ('PENDING','CONFIRMED') and a.slot.startAt<:endAt and a.slot.endAt>:startAt")
    List<Long> affectedPatientIds(Long doctorId, LocalDateTime startAt, LocalDateTime endAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a join fetch a.slot join fetch a.patient where a.doctor.id=:doctorId and a.status in ('PENDING','CONFIRMED') and a.slot.startAt<:endAt and a.slot.endAt>:startAt order by a.slot.startAt,a.id")
    List<Appointment> lockAffected(Long doctorId, LocalDateTime startAt, LocalDateTime endAt);

    @Query("select a from Appointment a join fetch a.slot join fetch a.patient where a.doctor.id=:doctorId and a.status in ('PENDING','CONFIRMED') and a.slot.startAt<:endAt and a.slot.endAt>:startAt order by a.slot.startAt,a.id")
    List<Appointment> affected(Long doctorId, LocalDateTime startAt, LocalDateTime endAt);

    interface StatusCount {
        AppointmentStatus getStatus();
        long getTotal();
    }

    @Query("SELECT a.status AS status, COUNT(a) AS total FROM Appointment a WHERE a.doctor.id = :doctorId GROUP BY a.status")
    List<StatusCount> countStatuses(Long doctorId);

    boolean existsByDoctor_IdAndStatusIn(Long doctorId, Collection<AppointmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor JOIN FETCH a.slot WHERE a.id = :id")
    Optional<Appointment> lockById(Long id);

    interface Target {
        Long getPatientId();
        Long getDoctorId();
        Long getSlotId();
        Long getScheduleId();
    }

    @Query("SELECT a.patient.id AS patientId, a.doctor.id AS doctorId, a.slot.id AS slotId, a.slot.schedule.id AS scheduleId FROM Appointment a WHERE a.id = :id")
    Optional<Target> findTargetById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status IN :statuses " +
            "AND a.slot.startAt < :endAt AND a.slot.endAt > :startAt")
    List<Appointment> findOverlappingAppointments(Long patientId, Collection<AppointmentStatus> statuses,
                                                LocalDateTime startAt, LocalDateTime endAt);

    @Query("SELECT a.id FROM Appointment a WHERE a.status = :status AND a.slot.endAt <= :now ORDER BY a.id")
    List<Long> findDueIds(AppointmentStatus status, LocalDateTime now, Pageable pageable);

    @Query(value = "SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor JOIN FETCH a.slot " +
            "WHERE (:patientId IS NULL OR a.patient.id = :patientId) " +
            "AND (:doctorId IS NULL OR a.doctor.id = :doctorId) " +
            "AND (:status IS NULL OR a.status = :status)",
            countQuery = "SELECT COUNT(a) FROM Appointment a " +
                    "WHERE (:patientId IS NULL OR a.patient.id = :patientId) " +
                    "AND (:doctorId IS NULL OR a.doctor.id = :doctorId) " +
                    "AND (:status IS NULL OR a.status = :status)")
    Page<Appointment> findFiltered(Long patientId, Long doctorId, AppointmentStatus status, Pageable pageable);
}
