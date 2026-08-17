package com.xxxx.clinicbookingsystem.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AccountRegisterRequest {
    private String username;
    @Size(min = 8, max = 64, message = "Password ngan")
    private String password;
    @Email(message = "Email is invalid")
    private String email;
    private String phone;
}
