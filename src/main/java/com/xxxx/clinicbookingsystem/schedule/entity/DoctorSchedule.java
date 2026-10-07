package com.xxxx.clinicbookingsystem.schedule.entity;

import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doctor_schedules")
@Getter
@Setter
@NoArgsConstructor
public class DoctorSchedule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startAt ASC")
    private List<ScheduleSlot> slots = new ArrayList<>();

    public void addSlot(ScheduleSlot slot) {
        slot.setSchedule(this);
        slot.setDoctor(doctor);
        slots.add(slot);
    }
}
