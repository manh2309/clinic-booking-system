package com.xxxx.clinicbookingsystem.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppointmentReasonRequest(@NotBlank @Size(max = 500) String reason) {}
