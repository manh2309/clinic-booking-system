package com.xxxx.clinicbookingsystem.appointment.visit;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="clinic_visit_events") @Getter @Setter @NoArgsConstructor
public class VisitEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="visit_id") private ClinicVisit visit;
    @Column(nullable=false,length=30) private String action;
    private Long actorId;
    private LocalDateTime occurredAt;
    @Column(length=500) private String reason;
}
