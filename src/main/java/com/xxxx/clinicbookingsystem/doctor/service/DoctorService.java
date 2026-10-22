package com.xxxx.clinicbookingsystem.doctor.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository repository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final SpecialtyService specialtyService;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;

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

    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request) {
        Doctor doctor = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));

        String fullName = request.fullName().trim();
        if (fullName.length() < 4) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Specialty specialty = specialtyService.getActive(request.specialtyId());
        doctor.setFullName(fullName);
        doctor.setQualification(request.qualification());
        doctor.setDescription(request.description());
        doctor.setSpecialty(specialty);

        return toResponse(doctor);
    }

    @Transactional
    public void updateStatus(Long id, UpdateDoctorStatusRequest request) {
        Doctor doctor = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));

        if (doctor.getActive().equals(request.active())) {
            return;
        }

        if (Boolean.FALSE.equals(request.active())) {
            boolean hasPendingAppointments = appointmentRepository.existsByDoctor_IdAndStatusIn(
                    id,
                    List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
            );
            if (hasPendingAppointments) {
                throw new AppException(ErrorCode.DOCTOR_HAS_PENDING_APPOINTMENTS);
            }
        } else {
            specialtyService.getActive(doctor.getSpecialty().getId());
        }

        doctor.setActive(request.active());
    }

    public Doctor getActive(Long id) {
        Doctor doctor = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        requireAvailable(doctor);
        return doctor;
    }

    public Doctor getByAccountId(Long accountId) {
        return repository.findByAccountId(accountId).orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
    }

    public void requireAvailable(Doctor doctor) {
        if (!Boolean.TRUE.equals(doctor.getActive())
                || !Boolean.TRUE.equals(doctor.getAccount().getIsActive())
                || !Boolean.TRUE.equals(doctor.getSpecialty().getActive())) {
            throw new AppException(ErrorCode.DOCTOR_NOT_AVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public DoctorResponse findByAccountId(Long accountId) {
        return toResponse(getByAccountId(accountId));
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> findAllForAdmin(int page, int size) {
        var pageable = PageRequest.of(
                page,
                Math.min(size, 100),
                Sort.by("id").ascending()
        );

        return PageResponse.from(
                repository.findAllForAdmin(pageable)
                        .map(this::toResponse)
        );
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getFullName(), doctor.getQualification(), doctor.getDescription(),
                doctor.getSpecialty().getId(), doctor.getSpecialty().getName(), doctor.getActive());
    }
}
