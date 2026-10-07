package com.xxxx.clinicbookingsystem.common.config;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.role.entity.Role;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:}") private String username;
    @Value("${app.bootstrap.admin.password:}") private String password;
    @Value("${app.bootstrap.admin.email:}") private String email;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank() || email.isBlank()) return;
        String normalizedUsername = username.trim().toLowerCase();
        if (accountRepository.existsByUsername(normalizedUsername)) return;
        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        Account admin = new Account();
        admin.setUsername(normalizedUsername);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setEmail(email.trim().toLowerCase());
        admin.setRole(adminRole);
        admin.setIsActive(true);
        accountRepository.save(admin);
    }
}
