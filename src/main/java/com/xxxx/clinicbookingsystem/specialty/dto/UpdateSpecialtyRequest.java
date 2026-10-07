package com.xxxx.clinicbookingsystem.specialty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSpecialtyRequest(
        @NotBlank(message = "Tên chuyên khoa không được bỏ trống")
        @Size(max = 100)
        String name,

        @Size(max = 1000)
        String description
) {}