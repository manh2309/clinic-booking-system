package com.xxxx.clinicbookingsystem.schedule.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.schedule.dto.*;
import com.xxxx.clinicbookingsystem.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService service;

    @PostMapping("/api/v1/doctors/me/schedules")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/api/v1/doctors/me/schedules")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<ScheduleResponse>> mySchedules() {
        return ApiResponse.success(service.mySchedules());
    }

    @DeleteMapping("/api/v1/doctors/me/schedules/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/doctors/{doctorId}/available-slots")
    public ApiResponse<List<SlotResponse>> availableSlots(@PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(service.availableSlots(doctorId, date));
    }
}
