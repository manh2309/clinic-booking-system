package com.xxxx.clinicbookingsystem.auth.service.impl;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.mapper.AccountMapper;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.auth.dto.request.AccountRegisterRequest;
import com.xxxx.clinicbookingsystem.auth.dto.request.LoginRequest;
import com.xxxx.clinicbookingsystem.auth.dto.response.AuthenticationResponse;
import com.xxxx.clinicbookingsystem.auth.jwt.JwtService;
import com.xxxx.clinicbookingsystem.auth.service.AuthenticationService;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.patient.entity.PatientProfile;
import com.xxxx.clinicbookingsystem.patient.repository.PatientProfileRepository;
import com.xxxx.clinicbookingsystem.role.entity.Role;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationServiceImpl implements AuthenticationService {
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PatientProfileRepository patientProfileRepository;

    @Override
    @Transactional
    public AccountResponse register(AccountRegisterRequest request) {

        String username = request.getUsername().trim().toLowerCase();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullName = request.getFullName().trim();
        if (accountRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }

        if (accountRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        if (fullName.length() < 4) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Role patientRole = roleRepository.findByRoleName("PATIENT")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        Account account = new Account();
        account.setUsername(username);
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setPhone(request.getPhone());
        account.setIsActive(true);
        account.setRole(patientRole);

        Account savedAccount = accountRepository.save(account);
        PatientProfile patientProfile = new PatientProfile();
        patientProfile.setAccount(savedAccount);
        patientProfile.setFullName(fullName);
        patientProfileRepository.save(patientProfile);

        return accountMapper.toResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.getUsername().trim().toLowerCase()).orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        if(!Boolean.TRUE.equals(account.getIsActive())) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }
        String accessToken = jwtService.generateToken(account.getUsername());

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .account(accountMapper.toResponse(account))
                .build();
    }
}
