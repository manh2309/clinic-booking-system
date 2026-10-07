package com.xxxx.clinicbookingsystem.appointment.controller;

import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.common.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/doctors/me/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
@Validated
public class DoctorAppointmentController {
    private final AppointmentService service;

    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> findAll(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.doctorAppointments(page, size));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AppointmentResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return ApiResponse.success(service.updateStatus(id, request));
    }
}
