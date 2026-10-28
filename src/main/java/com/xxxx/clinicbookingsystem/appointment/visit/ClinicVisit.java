package com.xxxx.clinicbookingsystem.appointment.visit;

import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="clinic_visits") @Getter @Setter @NoArgsConstructor
public class ClinicVisit extends BaseEntity {
    public enum Status { WAITING, IN_PROGRESS, COMPLETED, CANCELED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="appointment_id",unique=true) private Appointment appointment;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="active_doctor_id",unique=true) private Long activeDoctorId;
    private LocalDateTime checkedInAt;
    private Long checkedInBy;
    private LocalDateTime priorityAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    @Column(length=500) private String cancellationReason;
}
