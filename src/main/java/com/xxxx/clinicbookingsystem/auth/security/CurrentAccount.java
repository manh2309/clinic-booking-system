package com.xxxx.clinicbookingsystem.auth.security;

import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccount {
    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails user) return user.getAccountId();
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
