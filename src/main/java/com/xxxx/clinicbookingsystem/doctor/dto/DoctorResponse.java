package com.xxxx.clinicbookingsystem.doctor.dto;

public record DoctorResponse(Long id, String fullName, String qualification, String description,
                             Long specialtyId, String specialtyName) {}
