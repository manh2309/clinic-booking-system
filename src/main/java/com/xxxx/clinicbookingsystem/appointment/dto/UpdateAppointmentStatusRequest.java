package com.xxxx.clinicbookingsystem.appointment.dto;

import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status) {}
