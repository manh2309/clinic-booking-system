package com.xxxx.clinicbookingsystem.appointment.dto;

import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import java.time.LocalDateTime;

public record AppointmentResponse(Long id, AppointmentStatus status, Long doctorId, String doctorName,
                                  Long patientAccountId, LocalDateTime startAt, LocalDateTime endAt) {}
