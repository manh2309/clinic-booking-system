package com.xxxx.clinicbookingsystem.specialty.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSpecialtyStatusRequest(
        @NotNull(message = "Trạng thái chuyên khoa không được bỏ trống")
        Boolean active
) {}
