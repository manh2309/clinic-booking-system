package com.xxxx.clinicbookingsystem.schedule.repository;

import com.xxxx.clinicbookingsystem.schedule.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {
    @Query("SELECT s FROM ScheduleSlot s WHERE s.doctor.id = :doctorId AND s.schedule.active = true " +
            "AND NOT EXISTS (SELECT l.id FROM DoctorLeave l WHERE l.doctor.id=s.doctor.id AND l.status IN ('PENDING','APPROVED') AND l.startAt<s.endAt AND l.endAt>s.startAt) " +
            "AND s.status = :status AND s.startAt >= :from AND s.startAt < :to AND s.startAt > :now ORDER BY s.startAt")
    List<ScheduleSlot> findAvailable(Long doctorId, SlotStatus status, LocalDateTime from, LocalDateTime to, LocalDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleSlot s where s.doctor.id=:doctorId and s.startAt<:endAt and s.endAt>:startAt order by s.id")
    List<ScheduleSlot> lockRange(Long doctorId, LocalDateTime startAt, LocalDateTime endAt);
    boolean existsByScheduleIdAndStatus(Long scheduleId, SlotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScheduleSlot s JOIN FETCH s.doctor WHERE s.id = :id")
    Optional<ScheduleSlot> lockById(Long id);

    interface BookingTarget {
        Long getDoctorId();
        Long getScheduleId();
    }

    @Query("SELECT s.doctor.id AS doctorId, s.schedule.id AS scheduleId FROM ScheduleSlot s WHERE s.id = :slotId")
    Optional<BookingTarget> findBookingTarget(@Param("slotId") Long slotId);
}
