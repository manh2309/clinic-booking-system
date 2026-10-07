package com.xxxx.clinicbookingsystem.schedule.repository;

import com.xxxx.clinicbookingsystem.schedule.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {
    List<ScheduleSlot> findAllByDoctorIdAndStatusAndStartAtBetweenOrderByStartAtAsc(Long doctorId, SlotStatus status,
                                                                                    LocalDateTime from, LocalDateTime to);
    boolean existsByScheduleIdAndStatus(Long scheduleId, SlotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScheduleSlot s JOIN FETCH s.doctor WHERE s.id = :id")
    Optional<ScheduleSlot> lockById(Long id);
}
