package com.xxxx.clinicbookingsystem.appointment.entity;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.schedule.entity.ScheduleSlot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointment_patient", columnList = "patient_account_id"),
        @Index(name = "idx_appointment_doctor", columnList = "doctor_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private Account patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private ScheduleSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;
}
