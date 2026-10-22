package com.xxxx.clinicbookingsystem.schedule.planning;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave,Long>  {
    interface Range  {
        Long getDoctorId();
        java.time.LocalDateTime getStartAt();
        java.time.LocalDateTime getEndAt();
    }
    @Query("select l.doctor.id as doctorId,l.startAt as startAt,l.endAt as endAt from DoctorLeave l where l.id=:id") Optional<Range> range(Long id);
    @Query("select l.doctor.id from DoctorLeave l where l.id=:id") Optional<Long> doctorId(Long id);
    @Query("select l from DoctorLeave l join fetch l.doctor where (:doctorId is null or l.doctor.id=:doctorId) order by l.startAt desc,l.id desc") List<DoctorLeave> list(Long doctorId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from DoctorLeave l where l.id=:id") Optional<DoctorLeave> lockById(Long id);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select l from DoctorLeave l where l.doctor.id=:doctorId and l.status in ('PENDING','APPROVED') and l.startAt<:endAt and l.endAt>:startAt")
    List<DoctorLeave> lockBlocks(Long doctorId,LocalDateTime startAt,LocalDateTime endAt);
    @Query("select count(l)>0 from DoctorLeave l where l.doctor.id=:doctorId and l.status in ('PENDING','APPROVED') and l.startAt<:endAt and l.endAt>:startAt")
    boolean blocks(Long doctorId,LocalDateTime startAt,LocalDateTime endAt);
}
