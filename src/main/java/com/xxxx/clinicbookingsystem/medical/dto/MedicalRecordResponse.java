package com.xxxx.clinicbookingsystem.medical.dto;

import java.time.LocalDateTime;

public record MedicalRecordResponse(Long id, Long appointmentId, Long doctorId, String doctorName,
                                    LocalDateTime startAt, String diagnosis, String examinationNotes,
                                    String treatmentAdvice, LocalDateTime createdAt) {}
