package com.xxxx.clinicbookingsystem.medical.dto;

import jakarta.validation.constraints.*;

public record MedicalRecordRequest(
        @NotBlank @Size(max = 5000) String diagnosis,
        @Size(max = 5000) String examinationNotes,
        @Size(max = 5000) String treatmentAdvice) {}
