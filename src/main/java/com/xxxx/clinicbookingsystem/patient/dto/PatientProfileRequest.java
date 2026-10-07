package com.xxxx.clinicbookingsystem.patient.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PatientProfileRequest(
        @NotBlank
        @Size(min = 4, max = 100) String fullName,
        @PastOrPresent(message = "Ngày sinh không được nằm trong tương lai") LocalDate dateOfBirth,
        @Size(max = 20)
        @Pattern(
                regexp = "MALE|FEMALE|OTHER",
                message = "Giới tính phải là MALE, FEMALE hoặc OTHER"
        )
        String gender,
        @NotBlank @Email @Size(max = 100) String email,
        @Pattern(regexp = "^$|^[0-9+]{9,15}$") String phone,
        @Size(max = 500) String address
) {}
