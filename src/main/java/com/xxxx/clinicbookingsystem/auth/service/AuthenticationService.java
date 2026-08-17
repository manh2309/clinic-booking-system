package com.xxxx.clinicbookingsystem.auth.service;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.auth.dto.AccountRegisterRequest;
import com.xxxx.clinicbookingsystem.auth.dto.LoginRequest;

public interface AuthenticationService {
    AccountResponse register(AccountRegisterRequest request);
    AccountResponse login(LoginRequest request);
}
