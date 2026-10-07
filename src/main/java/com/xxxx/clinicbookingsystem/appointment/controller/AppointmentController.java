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
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Validated
public class AppointmentController {
    private final AppointmentService service;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<AppointmentResponse> book(@Valid @RequestBody CreateAppointmentRequest request) {
        return ApiResponse.success(service.book(request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<PageResponse<AppointmentResponse>> mine(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.mine(page, size));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<AppointmentResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(service.cancel(id));
    }
}
