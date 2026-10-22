package com.xxxx.clinicbookingsystem.schedule.planning;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="doctor_leaves") @Getter @Setter @NoArgsConstructor
public class DoctorLeave extends BaseEntity  {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="doctor_id") private Doctor doctor;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    @Column(length=500) private String reason;
    @Column(length=20) private String status="PENDING";
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length=500) private String reviewReason;
}
