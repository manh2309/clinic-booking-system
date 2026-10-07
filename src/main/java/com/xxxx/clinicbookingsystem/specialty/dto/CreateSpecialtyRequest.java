package com.xxxx.clinicbookingsystem.specialty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSpecialtyRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description
) {}
