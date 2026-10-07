package com.xxxx.clinicbookingsystem.appointment.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository repository;
    private final ScheduleSlotRepository slotRepository;
    private final AccountRepository accountRepository;
    private final DoctorService doctorService;
    private final CurrentAccount currentAccount;

    @Transactional
    public AppointmentResponse book(CreateAppointmentRequest request) {
        ScheduleSlot slot = slotRepository.lockById(request.slotId())
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        if (slot.getStatus() != SlotStatus.AVAILABLE || !slot.getStartAt().isAfter(LocalDateTime.now())
                || !Boolean.TRUE.equals(slot.getSchedule().getActive())) {
            throw new AppException(ErrorCode.SLOT_ALREADY_BOOKED);
        }
        Account patient = accountRepository.findById(currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(slot.getDoctor());
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING);
        slot.setStatus(SlotStatus.BOOKED);
        return toResponse(repository.save(appointment));
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> mine(int page, int size) {
        return PageResponse.from(repository.findPatientAppointments(currentAccount.id(),
                PageRequest.of(page, Math.min(size, 100))).map(this::toResponse));
    }

    @Transactional
    public AppointmentResponse cancel(Long id) {
        Appointment appointment = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!appointment.getPatient().getId().equals(currentAccount.id())
                || !(appointment.getStatus() == AppointmentStatus.PENDING || appointment.getStatus() == AppointmentStatus.CONFIRMED)
                || !appointment.getSlot().getStartAt().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointment.getSlot().setStatus(SlotStatus.AVAILABLE);
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> doctorAppointments(int page, int size) {
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        return PageResponse.from(repository.findDoctorAppointments(doctor.getId(),
                PageRequest.of(page, Math.min(size, 100))).map(this::toResponse));
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, UpdateAppointmentStatusRequest request) {
        Appointment appointment = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        boolean confirm = appointment.getStatus() == AppointmentStatus.PENDING && request.status() == AppointmentStatus.CONFIRMED;
        boolean complete = appointment.getStatus() == AppointmentStatus.CONFIRMED && request.status() == AppointmentStatus.COMPLETED;
        if (!appointment.getDoctor().getId().equals(doctor.getId()) || (!confirm && !complete)) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        appointment.setStatus(request.status());
        return toResponse(appointment);
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getStatus(), appointment.getDoctor().getId(),
                appointment.getDoctor().getFullName(), appointment.getPatient().getId(),
                appointment.getSlot().getStartAt(), appointment.getSlot().getEndAt());
    }
}
