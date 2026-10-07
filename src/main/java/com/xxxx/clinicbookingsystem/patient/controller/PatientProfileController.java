package com.xxxx.clinicbookingsystem.patient.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.patient.dto.PatientProfileRequest;
import com.xxxx.clinicbookingsystem.patient.dto.PatientProfileResponse;
import com.xxxx.clinicbookingsystem.patient.service.PatientProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientProfileController {
    private final PatientProfileService patientProfileService;

    @GetMapping("/me/profile")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<PatientProfileResponse> myProfile() {
        return ApiResponse.success(patientProfileService.myProfile());
    }

    @PutMapping("/me/profile/update")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<Void> updateMyProfile(@Valid @RequestBody PatientProfileRequest request) {
            patientProfileService.updateMyProfile(request);
            return ApiResponse.success();
    }
}
