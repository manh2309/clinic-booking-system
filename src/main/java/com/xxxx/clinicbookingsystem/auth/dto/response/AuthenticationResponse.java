package com.xxxx.clinicbookingsystem.auth.dto.response;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthenticationResponse {
    private String accessToken;
    private String tokenType;
    private AccountResponse account;
}
