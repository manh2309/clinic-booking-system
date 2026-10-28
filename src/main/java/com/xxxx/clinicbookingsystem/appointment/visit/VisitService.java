package com.xxxx.clinicbookingsystem.appointment.visit;

import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentReasonRequest;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import com.xxxx.clinicbookingsystem.patient.repository.PatientProfileRepository;
import com.xxxx.clinicbookingsystem.schedule.planning.DoctorLeaveRepository;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.xxxx.clinicbookingsystem.appointment.visit.ClinicVisit.Status.*;
import static com.xxxx.clinicbookingsystem.notification.entity.NotificationType.*;

@Service @RequiredArgsConstructor
public class VisitService {
    private final VisitRepository visits;
    private final VisitEventRepository events;
    private final AppointmentRepository appointments;
    private final AccountRepository accounts;
    private final DoctorRepository doctors;
    private final DoctorService doctorService;
    private final ScheduleRepository schedules;
    private final ScheduleSlotRepository slots;
    private final DoctorLeaveRepository leaves;
    private final PatientProfileRepository profiles;
    private final NotificationService notifications;
    private final CurrentAccount current;
    private final Clock clock;

    @Transactional
    public VisitDtos.Response checkIn(Long appointmentId) {
        var a = lockAppointment(appointmentId);
        requireConfirmed(a);
        if (!Boolean.TRUE.equals(a.getPatient().getIsActive())) throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        var now = now();
        if (!now.toLocalDate().equals(a.getSlot().getStartAt().toLocalDate())
                || now.isBefore(a.getSlot().getStartAt().minusMinutes(30))
                || now.isAfter(a.getSlot().getEndAt().plusMinutes(60))) throw new AppException(ErrorCode.VISIT_TIME_INVALID);
        requireAvailable(a);
        var v = visits.lockForAppointment(appointmentId).orElseGet(ClinicVisit::new);
        if (v.getStatus() == WAITING || v.getStatus() == IN_PROGRESS) return response(v);
        if (v.getStatus() == COMPLETED) throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        v.setAppointment(a); v.setStatus(WAITING); v.setActiveDoctorId(null);
        v.setCheckedInAt(now); v.setCheckedInBy(current.id());
        v.setPriorityAt(now.isAfter(a.getSlot().getStartAt()) ? now : a.getSlot().getStartAt());
        v.setStartedAt(null); v.setCompletedAt(null); v.setCanceledAt(null); v.setCancellationReason(null);
        visits.saveAndFlush(v); record(v, "CHECKED_IN", null);
        notifications.appointmentChanged(a, VISIT_CHECKED_IN);
        return response(v);
    }

    @Transactional
    public VisitDtos.Response cancelReception(Long appointmentId, AppointmentReasonRequest request) {
        lockAppointment(appointmentId);
        var v = visits.lockForAppointment(appointmentId).orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_WAITING));
        if (v.getStatus() != WAITING) throw new AppException(ErrorCode.VISIT_NOT_WAITING);
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 500)
            throw new AppException(ErrorCode.INVALID_REQUEST);
        v.setStatus(CANCELED); v.setCanceledAt(now()); v.setCancellationReason(request.reason().trim());
        record(v, "CANCELED", request.reason().trim());
        notifications.appointmentChanged(v.getAppointment(), VISIT_CANCELED);
        return response(v);
    }

    @Transactional
    public VisitDtos.Response start(Long appointmentId) {
        var a = lockAppointment(appointmentId);
        if (!a.getDoctor().getAccount().getId().equals(current.id())) throw new AppException(ErrorCode.ACCESS_DENIED);
        requireConfirmed(a);
        var v = visits.lockForAppointment(appointmentId).orElseThrow(() -> new AppException(ErrorCode.VISIT_REQUIRED));
        if (v.getStatus() == IN_PROGRESS) return response(v);
        if (v.getStatus() != WAITING) throw new AppException(ErrorCode.VISIT_NOT_WAITING);
        if (!now().toLocalDate().equals(a.getSlot().getStartAt().toLocalDate()) || now().isBefore(a.getSlot().getStartAt()))
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        requireAvailable(a);
        if (visits.lockActive(a.getDoctor().getId()).isPresent()) throw new AppException(ErrorCode.DOCTOR_BUSY);
        v.setStatus(IN_PROGRESS); v.setActiveDoctorId(a.getDoctor().getId()); v.setStartedAt(now());
        visits.saveAndFlush(v); record(v, "STARTED", null);
        notifications.appointmentChanged(a, VISIT_STARTED);
        return response(v);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void requireNotReceived(Long appointmentId) {
        var v = visits.lockForAppointment(appointmentId).orElse(null);
        if (v != null && (v.getStatus() == WAITING || v.getStatus() == IN_PROGRESS))
            throw new AppException(ErrorCode.VISIT_ALREADY_RECEIVED);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void complete(Appointment appointment) {
        var v = visits.lockForAppointment(appointment.getId()).orElseThrow(() -> new AppException(ErrorCode.VISIT_REQUIRED));
        if (v.getStatus() != IN_PROGRESS || !appointment.getDoctor().getId().equals(v.getActiveDoctorId()))
            throw new AppException(ErrorCode.VISIT_REQUIRED);
        v.setStatus(COMPLETED); v.setActiveDoctorId(null); v.setCompletedAt(now());
        record(v, "COMPLETED", null);
    }

    @Transactional(readOnly=true)
    public VisitDtos.Board board(Long doctorId, LocalDate date, int page, int size, boolean own) {
        Long selected = own ? doctorService.getByAccountId(current.id()).getId() : doctorId;
        if (selected == null || !doctors.existsById(selected)) throw new AppException(ErrorCode.DOCTOR_NOT_FOUND);
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        var waiting = visits.waiting(selected, day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size, Sort.by("priorityAt", "id"))).map(this::response);
        var active = visits.findByActiveDoctorId(selected).map(this::response).orElse(null);
        return new VisitDtos.Board(day, active, PageResponse.from(waiting));
    }

    public boolean isReceived(Long appointmentId) {
        return visits.existsByAppointmentIdAndStatusIn(appointmentId, List.of(WAITING, IN_PROGRESS));
    }

    private Appointment lockAppointment(Long id) {
        var t = appointments.findTargetById(id).orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
        accounts.lockById(t.getPatientId()).orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        doctors.lockById(t.getDoctorId()).orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        schedules.lockById(t.getScheduleId()).orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        slots.lockById(t.getSlotId()).orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        return appointments.lockById(id).orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }
    private void requireConfirmed(Appointment a) {
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
    }
    private void requireAvailable(Appointment a) {
        doctorService.requireAvailable(a.getDoctor());
        if (!Boolean.TRUE.equals(a.getSlot().getSchedule().getActive())
                || !leaves.lockBlocks(a.getDoctor().getId(), a.getSlot().getStartAt(), a.getSlot().getEndAt()).isEmpty())
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
    }
    private void record(ClinicVisit visit, String action, String reason) {
        var event = new VisitEvent(); event.setVisit(visit); event.setAction(action); event.setActorId(current.id());
        event.setOccurredAt(now()); event.setReason(reason); events.save(event);
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private VisitDtos.Response response(ClinicVisit v) {
        var a = v.getAppointment();
        String name = profiles.findByAccount_Id(a.getPatient().getId()).map(p -> p.getFullName())
                .filter(n -> n != null && !n.isBlank()).orElse(a.getPatient().getUsername());
        return new VisitDtos.Response(v.getId(), a.getId(), a.getDoctor().getId(), a.getDoctor().getFullName(), name,
                a.getPatient().getPhone(), a.getSlot().getStartAt(), a.getSlot().getEndAt(), v.getStatus(),
                v.getCheckedInAt(), v.getStartedAt(), v.getCompletedAt(), v.getPriorityAt());
    }
    public static VisitDtos.Summary summary(ClinicVisit v) {
        return v == null ? null : new VisitDtos.Summary(v.getId(), v.getStatus(), v.getCheckedInAt(), v.getStartedAt(),
                v.getCompletedAt(), v.getCanceledAt(), v.getCancellationReason());
    }
}
