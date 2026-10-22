package com.xxxx.clinicbookingsystem.patient.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.patient.dto.PatientProfileRequest;
import com.xxxx.clinicbookingsystem.patient.dto.PatientProfileResponse;
import com.xxxx.clinicbookingsystem.patient.entity.PatientProfile;
import com.xxxx.clinicbookingsystem.patient.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PatientProfileService {
    private final PatientProfileRepository patientProfileRepository;
    private final CurrentAccount currentAccount;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public PatientProfileResponse myProfile() {
        Long accountId = currentAccount.id();
        PatientProfile patientProfile = patientProfileRepository.findByAccount_Id(accountId)
                .orElse(null);

        if (patientProfile == null) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
            return new PatientProfileResponse(null, null, null, null, account.getPhone(), account.getEmail(), null, accountId);
        }

        return toResponse(patientProfile);
    }

    @Transactional
    public void updateMyProfile(PatientProfileRequest request) {
        Long accountId = currentAccount.id();
        // Khóa tài khoản để hai lần lưu đầu tiên không tạo hai hồ sơ cho cùng người.
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        PatientProfile patientProfile = patientProfileRepository.findByAccount_Id(accountId)
                .orElseGet(() -> {
                    PatientProfile profile = new PatientProfile();
                    profile.setAccount(account);
                    return profile;
                });

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = request.fullName().trim();
        if (fullName.length() < 4) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        if (accountRepository.existsByEmailAndIdNot(email, accountId)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
        patientProfile.setFullName(fullName);
        patientProfile.setDateOfBirth(request.dateOfBirth());
        patientProfile.setGender(request.gender());
        patientProfile.setAddress(request.address());

        account.setEmail(email);
        account.setPhone(request.phone());
        patientProfileRepository.save(patientProfile);
    }


    private PatientProfileResponse toResponse(PatientProfile patientProfile) {
        return new PatientProfileResponse(patientProfile.getPatientProfileId(), patientProfile.getFullName(), patientProfile.getDateOfBirth(),
                patientProfile.getGender(), patientProfile.getAccount().getPhone(), patientProfile.getAccount().getEmail(), patientProfile.getAddress(), patientProfile.getAccount().getId());
    }
}
