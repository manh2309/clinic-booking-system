package com.xxxx.clinicbookingsystem.medical.service;

import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordResponse;
import com.xxxx.clinicbookingsystem.medical.entity.MedicalRecord;
import com.xxxx.clinicbookingsystem.medical.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicalRecordService {
    private final MedicalRecordRepository repository;
    private final CurrentAccount currentAccount;

    public PageResponse<MedicalRecordResponse> mine(int page, int size) {
        return PageResponse.from(repository.findByAppointmentPatientIdAndAppointmentStatus(currentAccount.id(),
                AppointmentStatus.COMPLETED, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appointment.slot.startAt", "id")))
                .map(this::toResponse));
    }

    public MedicalRecordResponse forPatient(Long appointmentId) {
        return toResponse(repository.findForPatient(appointmentId, currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND)));
    }

    public MedicalRecordResponse forDoctor(Long appointmentId) {
        return toResponse(repository.findForDoctor(appointmentId, currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND)));
    }

    private MedicalRecordResponse toResponse(MedicalRecord record) {
        var appointment = record.getAppointment();
        return new MedicalRecordResponse(record.getId(), appointment.getId(), appointment.getDoctor().getId(),
                appointment.getDoctor().getFullName(), appointment.getSlot().getStartAt(), record.getDiagnosis(),
                record.getExaminationNotes(), record.getTreatmentAdvice(), record.getCreatedDate());
    }
}
