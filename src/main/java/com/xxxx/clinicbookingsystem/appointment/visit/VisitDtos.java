package com.xxxx.clinicbookingsystem.appointment.visit;

import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import java.time.*;

public class VisitDtos {
    public record Summary(Long id, ClinicVisit.Status status, LocalDateTime checkedInAt, LocalDateTime startedAt,
                          LocalDateTime completedAt, LocalDateTime canceledAt, String cancellationReason) {}
    public record Response(Long id, Long appointmentId, Long doctorId, String doctorName, String patientName,
                           String patientPhone, LocalDateTime appointmentStartAt, LocalDateTime appointmentEndAt,
                           ClinicVisit.Status status, LocalDateTime checkedInAt, LocalDateTime startedAt,
                           LocalDateTime completedAt, LocalDateTime priorityAt) {}
    public record Board(LocalDate date, Response activeVisit, PageResponse<Response> waiting) {}
}
