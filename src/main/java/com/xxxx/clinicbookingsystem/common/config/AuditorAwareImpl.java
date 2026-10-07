package com.xxxx.clinicbookingsystem.common.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return Optional.of(user.getAccountId().toString());
        }
        return Optional.of("SYSTEM");
    }
}
