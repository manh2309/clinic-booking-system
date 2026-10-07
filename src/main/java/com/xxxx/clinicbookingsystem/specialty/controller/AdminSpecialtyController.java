package com.xxxx.clinicbookingsystem.specialty.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.specialty.dto.CreateSpecialtyRequest;
import com.xxxx.clinicbookingsystem.specialty.dto.SpecialtyResponse;
import com.xxxx.clinicbookingsystem.specialty.dto.UpdateSpecialtyRequest;
import com.xxxx.clinicbookingsystem.specialty.service.SpecialtyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/specialties")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSpecialtyController {
    private final SpecialtyService service;

    @PostMapping
    public ApiResponse<SpecialtyResponse> create(@Valid @RequestBody CreateSpecialtyRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SpecialtyResponse> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateSpecialtyRequest request) {
        return ApiResponse.success(service.update(id, request));
    }


}
