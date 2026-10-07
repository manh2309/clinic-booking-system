package com.xxxx.clinicbookingsystem.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50, message = "Username must contain 4 to 50 characters")
    private String username;
    @NotBlank
    @Size(min = 4, max = 100)
    private String fullName;
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must contain 8 to 64 characters")
    private String password;
    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 100, message = "Email is too long")
    private String email;
    @Pattern(regexp = "^$|^[0-9+]{9,15}$", message = "Phone is invalid")
    private String phone;
}
