package com.xxxx.clinicbookingsystem.account.controller;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.account.service.AccountService;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<AccountResponse>> getAllAccounts() {
        return ApiResponse.success(accountService.getAllAccounts());
    }
    @GetMapping("/{id}")
    public ApiResponse<AccountResponse> getAccountById(@PathVariable("id") Long id) {
        return ApiResponse.success(accountService.getAccountById(id));
    }
}
