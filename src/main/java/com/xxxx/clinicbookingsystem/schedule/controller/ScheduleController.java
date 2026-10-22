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
    private final java.time.Clock clock;

    @PostMapping("/api/v1/doctors/me/schedules")
    @PreAuthorize("denyAll()")
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/api/v1/doctors/me/schedules")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<ScheduleResponse>> mySchedules() {
        return ApiResponse.success(service.mySchedules());
    }

    @DeleteMapping("/api/v1/doctors/me/schedules/{id}")
    @PreAuthorize("denyAll()")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/doctors/{doctorId}/available-slots")
    public ApiResponse<List<SlotResponse>> availableSlots(@PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(service.availableSlots(doctorId, date));
    }

    @GetMapping("/api/v1/doctors/{doctorId}/available-dates")
    public ApiResponse<List<AvailableDateResponse>> availableDates(@PathVariable Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(service.availableDates(doctorId, from == null ? LocalDate.now(clock) : from, days));
    }

    @PatchMapping("/api/v1/doctors/me/schedules/{id}/restore")
    @PreAuthorize("denyAll()")
    public ApiResponse<ScheduleResponse> restore(@PathVariable Long id) {
        return ApiResponse.success(service.restore(id));
    }
}
