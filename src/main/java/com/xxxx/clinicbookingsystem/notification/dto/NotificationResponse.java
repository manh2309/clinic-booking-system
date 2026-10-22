package com.xxxx.clinicbookingsystem.notification.dto;

import com.xxxx.clinicbookingsystem.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(Long id, Long appointmentId, NotificationType type, String title,
                                   String message, boolean read, LocalDateTime readAt, LocalDateTime createdAt) {}
