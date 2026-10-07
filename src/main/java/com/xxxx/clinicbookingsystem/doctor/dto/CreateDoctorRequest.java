package com.xxxx.clinicbookingsystem.doctor.dto;

import jakarta.validation.constraints.*;

public record CreateDoctorRequest(
        @NotBlank @Size(min = 4, max = 50) String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Email @Size(max = 100) String email,
        @Pattern(regexp = "^$|^[0-9+]{9,15}$") String phone,
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 255) String qualification,
        @Size(max = 1000) String description,
        @NotNull Long specialtyId
) {}
