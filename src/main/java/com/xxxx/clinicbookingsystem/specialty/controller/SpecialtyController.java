package com.xxxx.clinicbookingsystem.specialty.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.specialty.dto.SpecialtyResponse;
import com.xxxx.clinicbookingsystem.specialty.service.SpecialtyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
public class SpecialtyController {
    private final SpecialtyService service;

    @GetMapping
    public ApiResponse<List<SpecialtyResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
