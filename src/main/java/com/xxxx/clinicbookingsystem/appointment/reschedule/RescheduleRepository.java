package com.xxxx.clinicbookingsystem.appointment.reschedule;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface RescheduleRepository extends JpaRepository<RescheduleProposal, Long> {
    @Query("select p.targetSlot.id from RescheduleProposal p where p.id=:id and p.appointment.id=:appointmentId")
    Optional<Long> targetSlotId(Long id, Long appointmentId);
    List<RescheduleProposal> findByAppointmentIdOrderByIdDesc(Long appointmentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RescheduleProposal p where p.appointment.id=:appointmentId order by p.id")
    List<RescheduleProposal> lockHistory(Long appointmentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RescheduleProposal p where p.id=:id")
    Optional<RescheduleProposal> lockById(Long id);
}
