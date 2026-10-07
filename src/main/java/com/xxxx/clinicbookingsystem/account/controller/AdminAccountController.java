package com.xxxx.clinicbookingsystem.account.controller;

import com.xxxx.clinicbookingsystem.account.dto.UpdateAccountStatusRequest;
import com.xxxx.clinicbookingsystem.account.service.AccountService;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountService accountService;
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        accountService.updateStatus(id, request);
        return ApiResponse.success();
    }
}
