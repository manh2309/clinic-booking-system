package com.xxxx.clinicbookingsystem.schedule.planning;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="leave_contacts",uniqueConstraints=@UniqueConstraint(columnNames= {
    "leave_id","appointment_id"
}
)) @Getter @Setter @NoArgsConstructor
public class LeaveContact  {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="leave_id") private DoctorLeave leave;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="appointment_id") private Appointment appointment;
    private LocalDateTime contactedAt;
    private Long contactedBy;
}
