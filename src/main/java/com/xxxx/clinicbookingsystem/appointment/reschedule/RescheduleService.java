package com.xxxx.clinicbookingsystem.appointment.reschedule;

import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
import com.xxxx.clinicbookingsystem.schedule.planning.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.xxxx.clinicbookingsystem.appointment.reschedule.RescheduleProposal.Status.*;
import static com.xxxx.clinicbookingsystem.notification.entity.NotificationType.*;

@Service @RequiredArgsConstructor
public class RescheduleService {
    private final RescheduleRepository proposals;
    private final AppointmentRepository appointments;
    private final AccountRepository accounts;
    private final DoctorRepository doctors;
    private final DoctorService doctorService;
    private final ScheduleRepository schedules;
    private final ScheduleSlotRepository slots;
    private final DoctorLeaveRepository leaves;
    private final LeaveContactRepository contacts;
    private final CurrentAccount current;
    private final NotificationService notifications;
    private final Clock clock;
    private final com.xxxx.clinicbookingsystem.appointment.visit.VisitService visitService;

    @Transactional
    public RescheduleDtos.Response propose(Long appointmentId, RescheduleDtos.Create request) {
        var a = lock(appointmentId, request.slotId());
        visitService.requireNotReceived(appointmentId);
        if (!eligible(a)) throw new AppException(ErrorCode.RESCHEDULE_CLOSED);
        var target = slots.findById(request.slotId()).orElseThrow();
        checkTarget(a, target);
        var now = now();
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 500
                || request.expiresAt() == null || !request.expiresAt().isAfter(now)
                || request.expiresAt().isAfter(now.plusDays(7))
                || !request.expiresAt().isBefore(target.getStartAt())
                || (open(a) && !request.expiresAt().isBefore(a.getSlot().getStartAt()))) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        for (var p : proposals.lockHistory(appointmentId)) {
            if (p.getStatus() == ACCEPTED) throw new AppException(ErrorCode.RESCHEDULE_CLOSED);
            if (p.getStatus() != PENDING) continue;
            var state = effectiveStatus(p);
            if (state == PENDING) throw new AppException(ErrorCode.RESCHEDULE_PENDING);
            finish(p, state);
        }
        proposals.flush();
        var p = new RescheduleProposal();
        p.setAppointment(a); p.setTargetSlot(target); p.setPendingAppointmentId(a.getId());
        p.setProposedBy(current.id()); p.setReason(request.reason().trim());
        p.setExpiresAt(request.expiresAt()); p.setStatus(PENDING);
        proposals.saveAndFlush(p);
        notifications.appointmentChanged(a, RESCHEDULE_PROPOSED);
        return response(p);
    }

    @Transactional(readOnly = true)
    public List<RescheduleDtos.Response> history(Long appointmentId, boolean patient) {
        var a = appointments.findById(appointmentId).orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (patient && !a.getPatient().getId().equals(current.id())) throw new AppException(ErrorCode.RESCHEDULE_NOT_FOUND);
        return proposals.findByAppointmentIdOrderByIdDesc(appointmentId).stream().map(this::response).toList();
    }

    @Transactional
    public RescheduleDtos.Response respond(Long appointmentId, Long id, String action, boolean patient) {
        var targetId = proposals.targetSlotId(id, appointmentId).orElseThrow(() -> new AppException(ErrorCode.RESCHEDULE_NOT_FOUND));
        var a = lock(appointmentId, targetId);
        if (patient && !a.getPatient().getId().equals(current.id())) throw new AppException(ErrorCode.RESCHEDULE_NOT_FOUND);
        var p = proposals.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESCHEDULE_NOT_FOUND));
        if (effectiveStatus(p) != PENDING) throw new AppException(ErrorCode.RESCHEDULE_CLOSED);
        if ("decline".equals(action)) {
            finish(p, DECLINED); notifications.appointmentChanged(a, RESCHEDULE_DECLINED);
        } else if ("withdraw".equals(action)) {
            finish(p, WITHDRAWN); notifications.appointmentChanged(a, RESCHEDULE_WITHDRAWN);
        } else if ("accept".equals(action)) {
            visitService.requireNotReceived(appointmentId);
            var target = p.getTargetSlot();
            checkTarget(a, target);
            if (!Boolean.TRUE.equals(a.getPatient().getIsActive())) throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
            boolean conflict = appointments.findOverlappingAppointments(a.getPatient().getId(),
                    List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED), target.getStartAt(), target.getEndAt())
                    .stream().anyMatch(other -> !other.getId().equals(a.getId()));
            if (conflict) throw new AppException(ErrorCode.PATIENT_SCHEDULE_CONFLICT);
            var replacement = new Appointment();
            replacement.setPatient(a.getPatient()); replacement.setDoctor(a.getDoctor()); replacement.setSlot(target);
            replacement.setStatus(AppointmentStatus.PENDING);
            target.setStatus(SlotStatus.BOOKED);
            appointments.saveAndFlush(replacement);
            if (open(a)) {
                a.setStatus(AppointmentStatus.CANCELED);
                a.setStatusReason("Đổi sang lịch #" + replacement.getId() + " theo đề xuất #" + p.getId());
                boolean reusable = Boolean.TRUE.equals(a.getSlot().getSchedule().getActive()) && a.getSlot().getStartAt().isAfter(now());
                a.getSlot().setStatus(reusable ? SlotStatus.AVAILABLE : SlotStatus.BLOCKED);
                notifications.appointmentChanged(a, APPOINTMENT_CANCELED);
            }
            p.setReplacement(replacement); finish(p, ACCEPTED);
            notifications.appointmentChanged(replacement, RESCHEDULE_ACCEPTED);
        } else throw new AppException(ErrorCode.INVALID_REQUEST);
        return response(p);
    }

    private Appointment lock(Long appointmentId, Long targetId) {
        var source = appointments.findTargetById(appointmentId).orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
        var target = slots.findBookingTarget(targetId).orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        if (!source.getDoctorId().equals(target.getDoctorId()) || source.getSlotId().equals(targetId))
            throw new AppException(ErrorCode.INVALID_REQUEST);
        accounts.lockById(source.getPatientId()).orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        doctors.lockById(source.getDoctorId()).orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        for (Long id : new TreeSet<>(List.of(source.getScheduleId(), target.getScheduleId())))
            schedules.lockById(id).orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        for (Long id : new TreeSet<>(List.of(source.getSlotId(), targetId)))
            slots.lockById(id).orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        return appointments.lockById(appointmentId).orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }

    private void checkTarget(Appointment a, ScheduleSlot target) {
        doctorService.requireAvailable(a.getDoctor());
        if (target.getStatus() != SlotStatus.AVAILABLE || !target.getStartAt().isAfter(now())
                || !Boolean.TRUE.equals(target.getSchedule().getActive())) throw new AppException(ErrorCode.SLOT_ALREADY_BOOKED);
        if (!leaves.lockBlocks(a.getDoctor().getId(), target.getStartAt(), target.getEndAt()).isEmpty())
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
    }

    private boolean open(Appointment a) {
        return a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED;
    }
    private boolean eligible(Appointment a) {
        if (visitService.isReceived(a.getId())) return false;
        return (open(a) && a.getSlot().getStartAt().isAfter(now()))
                || (a.getStatus() == AppointmentStatus.CANCELED && contacts.existsByAppointmentId(a.getId()));
    }
    private RescheduleProposal.Status effectiveStatus(RescheduleProposal p) {
        if (p.getStatus() != PENDING) return p.getStatus();
        if (!p.getExpiresAt().isAfter(now())) return EXPIRED;
        return eligible(p.getAppointment()) ? PENDING : INVALIDATED;
    }
    private void finish(RescheduleProposal p, RescheduleProposal.Status status) {
        p.setStatus(status); p.setPendingAppointmentId(null); p.setRespondedAt(now());
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private RescheduleDtos.Response response(RescheduleProposal p) {
        return new RescheduleDtos.Response(p.getId(), p.getAppointment().getId(), p.getTargetSlot().getId(),
                p.getTargetSlot().getStartAt(), p.getTargetSlot().getEndAt(), p.getReason(), effectiveStatus(p),
                p.getExpiresAt(), p.getRespondedAt(), p.getProposedBy(), p.getCreatedDate(),
                p.getReplacement() == null ? null : p.getReplacement().getId());
    }
}
