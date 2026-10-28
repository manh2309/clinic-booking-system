package com.xxxx.clinicbookingsystem.appointment.reschedule;

import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import com.xxxx.clinicbookingsystem.schedule.entity.ScheduleSlot;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "reschedule_proposals") @Getter @Setter @NoArgsConstructor
public class RescheduleProposal extends BaseEntity {
    public enum Status { PENDING, ACCEPTED, DECLINED, WITHDRAWN, EXPIRED, INVALIDATED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "appointment_id") private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_slot_id") private ScheduleSlot targetSlot;
    @Column(name = "pending_appointment_id", unique = true) private Long pendingAppointmentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "replacement_appointment_id") private Appointment replacement;
    @Column(name = "proposed_by", nullable = false) private Long proposedBy;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "responded_at") private LocalDateTime respondedAt;
}
