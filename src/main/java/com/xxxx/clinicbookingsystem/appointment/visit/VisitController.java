package com.xxxx.clinicbookingsystem.appointment.visit;

import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentReasonRequest;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor @Validated
public class VisitController {
    private final VisitService service;
    @PostMapping("/admin/appointments/{id}/check-in") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<VisitDtos.Response> checkIn(@PathVariable Long id) { return ApiResponse.success(service.checkIn(id)); }
    @PatchMapping("/admin/appointments/{id}/reception/cancel") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<VisitDtos.Response> cancel(@PathVariable Long id, @Valid @RequestBody AppointmentReasonRequest reason) {
        return ApiResponse.success(service.cancelReception(id, reason));
    }
    @PostMapping("/doctors/me/appointments/{id}/start") @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<VisitDtos.Response> start(@PathVariable Long id) { return ApiResponse.success(service.start(id)); }
    @GetMapping("/admin/queue") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<VisitDtos.Board> adminBoard(@RequestParam @Positive Long doctorId, @RequestParam(required=false) LocalDate date,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.board(doctorId, date, page, size, false));
    }
    @GetMapping("/doctors/me/queue") @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<VisitDtos.Board> doctorBoard(@RequestParam(required=false) LocalDate date,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.board(null, date, page, size, true));
    }
}
