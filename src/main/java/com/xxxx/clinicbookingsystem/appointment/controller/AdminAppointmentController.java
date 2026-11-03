package com.xxxx.clinicbookingsystem.appointment.controller;

import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentReasonRequest;
import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentResponse;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentController {
    private final AppointmentService service;

    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(defaultValue="") String keyword,@RequestParam(required=false) java.time.LocalDate from,@RequestParam(required=false) java.time.LocalDate to) {
        return ApiResponse.success(service.searchAppointments(false,page,size,status,doctorId,keyword,from,to));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<AppointmentResponse> cancel(@PathVariable Long id, @Valid @RequestBody AppointmentReasonRequest request) {
        return ApiResponse.success(service.adminCancel(id, request));
    }

    @PatchMapping("/{id}/no-show")
    public ApiResponse<AppointmentResponse> noShow(@PathVariable Long id, @Valid @RequestBody AppointmentReasonRequest request) {
        return ApiResponse.success(service.adminNoShow(id, request));
    }
}
