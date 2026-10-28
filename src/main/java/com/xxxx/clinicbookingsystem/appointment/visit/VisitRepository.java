package com.xxxx.clinicbookingsystem.appointment.visit;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDateTime;
import java.util.*;

public interface VisitRepository extends JpaRepository<ClinicVisit,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ClinicVisit v where v.appointment.id=:appointmentId")
    Optional<ClinicVisit> lockForAppointment(Long appointmentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ClinicVisit v where v.activeDoctorId=:doctorId")
    Optional<ClinicVisit> lockActive(Long doctorId);
    Optional<ClinicVisit> findByActiveDoctorId(Long doctorId);
    Optional<ClinicVisit> findByAppointmentId(Long appointmentId);
    List<ClinicVisit> findByAppointmentIdIn(Collection<Long> ids);
    boolean existsByAppointmentIdAndStatusIn(Long appointmentId, Collection<ClinicVisit.Status> statuses);
    @Query("select v from ClinicVisit v where v.appointment.doctor.id=:doctorId and v.status='WAITING' "
            + "and v.appointment.slot.startAt>=:from and v.appointment.slot.startAt<:to")
    Page<ClinicVisit> waiting(Long doctorId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
