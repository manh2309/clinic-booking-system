package com.xxxx.clinicbookingsystem.doctor.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.doctor.dto.*;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.entity.Role;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.service.SpecialtyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository repository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final SpecialtyService specialtyService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> search(Long specialtyId, String keyword, int page, int size) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return PageResponse.from(repository.search(specialtyId, normalized, PageRequest.of(page, Math.min(size, 100))).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DoctorResponse findById(Long id) {
        return toResponse(repository.findActiveByIdWithSpecialty(id).orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND)));
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();
        if (accountRepository.existsByUsername(username)) throw new AppException(ErrorCode.USERNAME_EXISTED);
        if (accountRepository.existsByEmail(email)) throw new AppException(ErrorCode.EMAIL_EXISTED);
        Role role = roleRepository.findByRoleName("DOCTOR").orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        Specialty specialty = specialtyService.getActive(request.specialtyId());

        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setEmail(email);
        account.setPhone(request.phone());
        account.setIsActive(true);
        account.setRole(role);
        accountRepository.save(account);

        Doctor doctor = new Doctor();
        doctor.setAccount(account);
        doctor.setSpecialty(specialty);
        doctor.setFullName(request.fullName().trim());
        doctor.setQualification(request.qualification());
        doctor.setDescription(request.description());
        return toResponse(repository.save(doctor));
    }

    public Doctor getActive(Long id) {
        return repository.findById(id).filter(d -> Boolean.TRUE.equals(d.getActive()))
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
    }

    public Doctor getByAccountId(Long accountId) {
        return repository.findByAccountIdAndActiveTrue(accountId).orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getFullName(), doctor.getQualification(), doctor.getDescription(),
                doctor.getSpecialty().getId(), doctor.getSpecialty().getName());
    }
}
