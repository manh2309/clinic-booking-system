package com.xxxx.clinicbookingsystem.medical.entity;

import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medical_records")
@Getter @Setter @NoArgsConstructor
public class MedicalRecord extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medical_record_id")
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagnosis;
    @Column(name = "examination_notes", columnDefinition = "TEXT")
    private String examinationNotes;
    @Column(name = "treatment_advice", columnDefinition = "TEXT")
    private String treatmentAdvice;
}
