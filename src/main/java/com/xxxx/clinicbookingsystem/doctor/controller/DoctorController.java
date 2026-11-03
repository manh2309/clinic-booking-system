package com.xxxx.clinicbookingsystem.doctor.controller;

import com.xxxx.clinicbookingsystem.common.response.*;
import com.xxxx.clinicbookingsystem.doctor.dto.DoctorResponse;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Validated
public class DoctorController {
    private final DoctorService service;
    private final CurrentAccount currentAccount;

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<DoctorResponse> me() {
        return ApiResponse.success(service.findByAccountId(currentAccount.id()));
    }

    @GetMapping
    public ApiResponse<PageResponse<DoctorResponse>> search(@RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.search(specialtyId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<DoctorResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }
}
