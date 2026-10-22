package com.xxxx.clinicbookingsystem.schedule.planning;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="weekly_schedules") @Getter @Setter @NoArgsConstructor
public class WeeklySchedule extends BaseEntity  {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="doctor_id") private Doctor doctor;
    private int dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private boolean active=true;
}
