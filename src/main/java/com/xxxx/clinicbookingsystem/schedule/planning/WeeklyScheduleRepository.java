package com.xxxx.clinicbookingsystem.schedule.planning;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface WeeklyScheduleRepository extends JpaRepository<WeeklySchedule,Long>  {
    List<WeeklySchedule> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);
    @Query("select distinct w.doctor.id from WeeklySchedule w where w.active=true") List<Long> activeDoctorIds();
}
