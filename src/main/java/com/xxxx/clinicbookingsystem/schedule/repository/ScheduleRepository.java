package com.xxxx.clinicbookingsystem.schedule.repository;

import com.xxxx.clinicbookingsystem.schedule.entity.DoctorSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DoctorSchedule s where s.doctor.id=:doctorId and s.startAt<:endAt and s.endAt>:startAt order by s.id")
    List<DoctorSchedule> lockRange(Long doctorId, LocalDateTime startAt, LocalDateTime endAt);
    long countByDoctorIdAndActiveTrueAndEndAtGreaterThan(Long doctorId, LocalDateTime now);
    boolean existsByDoctorIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(Long doctorId, LocalDateTime endAt, LocalDateTime startAt);
    boolean existsByDoctorIdAndActiveFalseAndStartAtLessThanAndEndAtGreaterThan(Long doctorId, LocalDateTime endAt, LocalDateTime startAt);
    List<DoctorSchedule> findAllByDoctorIdOrderByStartAtDesc(Long doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DoctorSchedule s WHERE s.id = :id AND s.doctor.id = :doctorId")
    Optional<DoctorSchedule> lockOwnedSchedule(Long id, Long doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DoctorSchedule s WHERE s.id = :id")
    Optional<DoctorSchedule> lockById(Long id);
}
