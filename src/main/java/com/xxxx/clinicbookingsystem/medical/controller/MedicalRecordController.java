package com.xxxx.clinicbookingsystem.medical.controller;

import com.xxxx.clinicbookingsystem.common.response.*;
import com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordResponse;
import com.xxxx.clinicbookingsystem.medical.service.MedicalRecordService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @Validated
public class MedicalRecordController {
    private final MedicalRecordService service;

    @GetMapping("/api/v1/patient/me/medical-records") @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<PageResponse<MedicalRecordResponse>> mine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.mine(page, size));
    }

    @GetMapping("/api/v1/patient/me/medical-records/{appointmentId}") @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<MedicalRecordResponse> forPatient(@PathVariable Long appointmentId) {
        return ApiResponse.success(service.forPatient(appointmentId));
    }

    @GetMapping("/api/v1/doctors/me/appointments/{appointmentId}/medical-record") @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<MedicalRecordResponse> forDoctor(@PathVariable Long appointmentId) {
        return ApiResponse.success(service.forDoctor(appointmentId));
    }
}
