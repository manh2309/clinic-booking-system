package com.xxxx.clinicbookingsystem.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateDoctorRequest(
        @NotBlank(message = "Tên bác sĩ không được bỏ trống")
        @Size(min = 4, max = 100)
        String fullName,

        @Size(max = 255)
        String qualification,

        @Size(max = 1000)
        String description,

        @NotNull(message = "Chuyên khoa không được bỏ trống")
        @Positive(message = "ID chuyên khoa phải lớn hơn 0")
        Long specialtyId
) {}
