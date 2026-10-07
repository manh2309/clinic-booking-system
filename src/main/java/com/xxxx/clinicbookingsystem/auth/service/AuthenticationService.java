package com.xxxx.clinicbookingsystem.auth.service;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.auth.dto.request.AccountRegisterRequest;
import com.xxxx.clinicbookingsystem.auth.dto.request.LoginRequest;
import com.xxxx.clinicbookingsystem.auth.dto.response.AuthenticationResponse;

public interface AuthenticationService {
    AccountResponse register(AccountRegisterRequest request);
    AuthenticationResponse login(LoginRequest request);
}
