package com.xxxx.clinicbookingsystem.schedule.entity;

import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_slots", uniqueConstraints = @UniqueConstraint(name = "uk_doctor_slot_start", columnNames = {"doctor_id", "start_at"}))
@Getter
@Setter
@NoArgsConstructor
public class ScheduleSlot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private DoctorSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status = SlotStatus.AVAILABLE;
}
