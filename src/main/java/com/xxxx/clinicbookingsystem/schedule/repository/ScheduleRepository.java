package com.xxxx.clinicbookingsystem.schedule.repository;

import com.xxxx.clinicbookingsystem.schedule.entity.DoctorSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    boolean existsByDoctorIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(Long doctorId, LocalDateTime endAt, LocalDateTime startAt);
    List<DoctorSchedule> findAllByDoctorIdAndActiveTrueOrderByStartAtAsc(Long doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DoctorSchedule s WHERE s.id = :id AND s.doctor.id = :doctorId AND s.active = true")
    Optional<DoctorSchedule> lockOwnedSchedule(Long id, Long doctorId);
}
