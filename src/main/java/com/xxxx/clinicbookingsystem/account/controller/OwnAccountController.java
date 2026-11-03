package com.xxxx.clinicbookingsystem.account.controller;

import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/account") @RequiredArgsConstructor
public class OwnAccountController {
    private final AccountRepository accounts;
    private final CurrentAccount current;
    private final PasswordEncoder encoder;
    public record Contact(String username, String email, String phone) {}
    public record ContactRequest(@Pattern(regexp="^$|[0-9+(). -]{8,20}") String phone) {}
    public record PasswordRequest(@NotBlank @Size(max=72) String currentPassword,
                                  @NotBlank @Size(min=8,max=64) String newPassword,
                                  @NotBlank @Size(max=64) String confirmPassword) {}

    @GetMapping @Transactional(readOnly=true)
    public ApiResponse<Contact> mine() {
        var a=accounts.findById(current.id()).orElseThrow(()->new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return ApiResponse.success(new Contact(a.getUsername(),a.getEmail(),a.getPhone()));
    }
    @PatchMapping @Transactional
    public ApiResponse<Contact> contact(@Valid @RequestBody ContactRequest request) {
        var a=accounts.lockById(current.id()).orElseThrow(()->new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        a.setPhone(request.phone()==null||request.phone().isBlank()?null:request.phone().trim());
        return ApiResponse.success(new Contact(a.getUsername(),a.getEmail(),a.getPhone()));
    }
    @PostMapping("/password") @Transactional
    public ApiResponse<Void> password(@Valid @RequestBody PasswordRequest request) {
        var a=accounts.lockById(current.id()).orElseThrow(()->new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        if(!encoder.matches(request.currentPassword(),a.getPassword()))throw new AppException(ErrorCode.INVALID_REQUEST);
        if(!request.newPassword().equals(request.confirmPassword()) || encoder.matches(request.newPassword(),a.getPassword())
                || request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new AppException(ErrorCode.INVALID_REQUEST);
        a.setPassword(encoder.encode(request.newPassword()));
        a.setTokenVersion(Math.addExact(a.getTokenVersion(),1));
        return ApiResponse.success();
    }
}
