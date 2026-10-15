package com.xxxx.clinicbookingsystem.doctor.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.doctor.dto.*;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/doctors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorController {
    private final DoctorService service;

    @PostMapping
    public ApiResponse<DoctorResponse> create(@Valid @RequestBody CreateDoctorRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DoctorResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDoctorRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDoctorStatusRequest request) {
        service.updateStatus(id, request);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResponse<DoctorResponse>> findAllForAdmin(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return ApiResponse.success(service.findAllForAdmin(page, size));
    }
}
