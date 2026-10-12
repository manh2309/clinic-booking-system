package com.xxxx.clinicbookingsystem.doctor.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateDoctorStatusRequest(
        @NotNull(message = "Trạng thái bác sĩ không được bỏ trống")
        Boolean active
) {}
