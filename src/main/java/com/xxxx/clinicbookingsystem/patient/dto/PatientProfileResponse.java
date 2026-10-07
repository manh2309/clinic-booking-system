package com.xxxx.clinicbookingsystem.patient.dto;

import java.time.LocalDate;

public record PatientProfileResponse(Long patientProfileId, String fullName, LocalDate dateOfBirth, String gender, String phone, String email,
                                     String address, Long accountId) {}
