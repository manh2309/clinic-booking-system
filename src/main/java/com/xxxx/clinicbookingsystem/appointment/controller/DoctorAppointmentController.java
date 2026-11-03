package com.xxxx.clinicbookingsystem.appointment.controller;

import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordRequest;
import com.xxxx.clinicbookingsystem.common.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;

@RestController
@RequestMapping("/api/v1/doctors/me/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
@Validated
public class DoctorAppointmentController {
    private final AppointmentService service;

    @GetMapping("/summary")
    public ApiResponse<DoctorAppointmentSummary> summary() {
        return ApiResponse.success(service.doctorSummary());
    }

    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> findAll(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue="") String keyword,@RequestParam(required=false) java.time.LocalDate from,@RequestParam(required=false) java.time.LocalDate to) {
        return ApiResponse.success(service.searchAppointments(true,page,size,status,null,keyword,from,to));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AppointmentResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return ApiResponse.success(service.updateStatus(id, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<AppointmentResponse> complete(@PathVariable Long id,
            @Valid @RequestBody MedicalRecordRequest request) {
        return ApiResponse.success(service.complete(id, request));
    }
}
