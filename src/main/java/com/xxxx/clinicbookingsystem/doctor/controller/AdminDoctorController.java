package com.xxxx.clinicbookingsystem.doctor.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.doctor.dto.*;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/doctors")
@RequiredArgsConstructor
public class AdminDoctorController {
    private final DoctorService service;

    @PostMapping
    public ApiResponse<DoctorResponse> create(@Valid @RequestBody CreateDoctorRequest request) {
        return ApiResponse.success(service.create(request));
    }
}
