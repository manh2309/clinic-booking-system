package com.xxxx.clinicbookingsystem.auth.controller;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.auth.dto.request.AccountRegisterRequest;
import com.xxxx.clinicbookingsystem.auth.dto.request.LoginRequest;
import com.xxxx.clinicbookingsystem.auth.dto.response.AuthenticationResponse;
import com.xxxx.clinicbookingsystem.auth.service.AuthenticationService;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    public final AuthenticationService authenticationService;
    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ApiResponse<AccountResponse> register(@Valid @RequestBody AccountRegisterRequest request) {
       return ApiResponse.success(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authenticationService.login(request));
    }
}
