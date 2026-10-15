package com.xxxx.clinicbookingsystem.appointment.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentResponse;
import com.xxxx.clinicbookingsystem.appointment.dto.CreateAppointmentRequest;
import com.xxxx.clinicbookingsystem.appointment.dto.UpdateAppointmentStatusRequest;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import com.xxxx.clinicbookingsystem.patient.repository.PatientProfileRepository;
import com.xxxx.clinicbookingsystem.schedule.entity.ScheduleSlot;
import com.xxxx.clinicbookingsystem.schedule.entity.SlotStatus;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository repository;
    private final ScheduleSlotRepository slotRepository;
    private final AccountRepository accountRepository;
    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;
    private final CurrentAccount currentAccount;
    private final PatientProfileRepository patientProfileRepository;

    @Transactional
    public AppointmentResponse book(CreateAppointmentRequest request) {
        Long doctorId = slotRepository.findDoctorIdBySlotId(request.slotId())
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));

        Doctor doctor = doctorRepository.lockById(doctorId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));

        ScheduleSlot slot = slotRepository.lockById(request.slotId())
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        if (slot.getStatus() != SlotStatus.AVAILABLE || !slot.getStartAt().isAfter(LocalDateTime.now())
                || !Boolean.TRUE.equals(slot.getSchedule().getActive())) {
            throw new AppException(ErrorCode.SLOT_ALREADY_BOOKED);
        }

        if (!Boolean.TRUE.equals(doctor.getActive())
                || !Boolean.TRUE.equals(doctor.getAccount().getIsActive())
                || !Boolean.TRUE.equals(doctor.getSpecialty().getActive())) {
            throw new AppException(ErrorCode.DOCTOR_NOT_AVAILABLE);
        }
        Account patient = accountRepository.findById(currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING);
        slot.setStatus(SlotStatus.BOOKED);
        return toResponse(repository.save(appointment));
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> mine(int page, int size) {
        var appointment = repository.findPatientAppointments(currentAccount.id(), PageRequest.of(page, Math.min(size, 100)));
        return toPageResponse(appointment);
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
        var appointments = repository.findDoctorAppointments(doctor.getId(), PageRequest.of(page, Math.min(size, 100)));
        return toPageResponse(appointments);
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
        Account patient = appointment.getPatient();

        String patientFullName = patientProfileRepository
                .findByAccount_Id(patient.getId())
                .map(profile -> profile.getFullName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(patient.getUsername());

        return toResponse(appointment, patientFullName);
    }

    private AppointmentResponse toResponse(
            Appointment appointment,
            String patientFullName) {

        Account patient = appointment.getPatient();

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getStatus(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getFullName(),
                patient.getId(),
                appointment.getSlot().getStartAt(),
                appointment.getSlot().getEndAt(),
                patientFullName,
                patient.getPhone()
        );
    }

    private PageResponse<AppointmentResponse> toPageResponse(
            Page<Appointment> appointments) {

        var accountIds = appointments.getContent().stream()
                .map(appointment -> appointment.getPatient().getId())
                .distinct()
                .toList();

        Map<Long, String> patientNames = new HashMap<>();

        if (!accountIds.isEmpty()) {
            var profiles = patientProfileRepository
                    .findAllByAccount_IdIn(accountIds);

            for (var profile : profiles) {
                String name = profile.getFullName();

                if (name != null && !name.isBlank()) {
                    patientNames.put(profile.getAccount().getId(), name);
                }
            }
        }

        return PageResponse.from(appointments.map(appointment -> {
            Account patient = appointment.getPatient();

            String name = patientNames.getOrDefault(
                    patient.getId(),
                    patient.getUsername()
            );

            return toResponse(appointment, name);
        }));
    }
}
