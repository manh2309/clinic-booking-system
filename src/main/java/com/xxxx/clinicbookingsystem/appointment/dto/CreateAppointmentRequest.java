package com.xxxx.clinicbookingsystem.appointment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAppointmentRequest(@NotNull Long slotId) {}
