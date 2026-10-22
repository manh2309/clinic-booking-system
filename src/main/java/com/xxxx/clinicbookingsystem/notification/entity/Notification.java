package com.xxxx.clinicbookingsystem.notification.entity;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_account_id", nullable = false) private Account recipient;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id") private Appointment appointment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private NotificationType type;
    @Column(nullable = false) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "is_read", nullable = false) private boolean read;
    @Column(name = "read_at") private LocalDateTime readAt;
}
