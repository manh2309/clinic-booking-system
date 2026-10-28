package com.xxxx.clinicbookingsystem.appointment.reschedule;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class RescheduleController {
    private final RescheduleService service;
    @PostMapping("/admin/appointments/{appointmentId}/reschedules") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RescheduleDtos.Response> propose(@PathVariable Long appointmentId, @Valid @RequestBody RescheduleDtos.Create request) {
        return ApiResponse.success(service.propose(appointmentId, request));
    }
    @GetMapping("/admin/appointments/{appointmentId}/reschedules") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RescheduleDtos.Response>> adminHistory(@PathVariable Long appointmentId) {
        return ApiResponse.success(service.history(appointmentId, false));
    }
    @PatchMapping("/admin/appointments/{appointmentId}/reschedules/{id}/withdraw") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RescheduleDtos.Response> withdraw(@PathVariable Long appointmentId, @PathVariable Long id) {
        return ApiResponse.success(service.respond(appointmentId, id, "withdraw", false));
    }
    @GetMapping("/appointments/{appointmentId}/reschedules") @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<List<RescheduleDtos.Response>> patientHistory(@PathVariable Long appointmentId) {
        return ApiResponse.success(service.history(appointmentId, true));
    }
    @PatchMapping("/appointments/{appointmentId}/reschedules/{id}/{action:accept|decline}") @PreAuthorize("hasRole('PATIENT')")
    public ApiResponse<RescheduleDtos.Response> respond(@PathVariable Long appointmentId, @PathVariable Long id, @PathVariable String action) {
        return ApiResponse.success(service.respond(appointmentId, id, action, true));
    }
}
