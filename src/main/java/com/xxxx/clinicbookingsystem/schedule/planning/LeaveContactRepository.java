package com.xxxx.clinicbookingsystem.schedule.planning;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface LeaveContactRepository extends JpaRepository<LeaveContact,Long>  {
    boolean existsByAppointmentId(Long appointmentId);
    @Query("select c from LeaveContact c join fetch c.appointment a join fetch a.patient join fetch a.slot where c.leave.id=:leaveId order by a.slot.startAt,a.id") List<LeaveContact> forLeave(Long leaveId);
}
