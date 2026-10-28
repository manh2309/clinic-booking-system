package com.xxxx.clinicbookingsystem.appointment.reschedule;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class RescheduleDtos {
    public record Create(@NotNull @Positive Long slotId, @NotBlank @Size(max=500) String reason,
                         @NotNull LocalDateTime expiresAt) {}
    public record Response(Long id, Long appointmentId, Long targetSlotId, LocalDateTime targetStartAt,
                           LocalDateTime targetEndAt, String reason, RescheduleProposal.Status status,
                           LocalDateTime expiresAt, LocalDateTime respondedAt, Long proposedBy,
                           LocalDateTime createdAt, Long replacementAppointmentId) {}
}
