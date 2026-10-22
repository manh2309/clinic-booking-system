package com.xxxx.clinicbookingsystem.notification.controller;

import com.xxxx.clinicbookingsystem.common.response.*;
import com.xxxx.clinicbookingsystem.notification.dto.NotificationResponse;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/notifications/me")
@RequiredArgsConstructor @Validated @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
public class NotificationController {
    private final NotificationService service;
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> mine(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.mine(page, size));
    }
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() { return ApiResponse.success(service.unreadCount()); }
    @PatchMapping("/read-all")
    public ApiResponse<Integer> markAllRead() { return ApiResponse.success(service.markAllRead()); }
    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.success(service.markRead(id));
    }
}
