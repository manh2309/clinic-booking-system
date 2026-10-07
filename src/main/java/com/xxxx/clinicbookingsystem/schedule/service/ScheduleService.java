package com.xxxx.clinicbookingsystem.schedule.service;

import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import com.xxxx.clinicbookingsystem.schedule.dto.*;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {
    private static final int SLOT_MINUTES = 30;
    private final ScheduleRepository repository;
    private final ScheduleSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorService doctorService;
    private final CurrentAccount currentAccount;

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        validateTime(request.startAt(), request.endAt());
        Doctor doctor = doctorRepository.lockByAccountId(currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        if (repository.existsByDoctorIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(
                doctor.getId(), request.endAt(), request.startAt())) {
            throw new AppException(ErrorCode.SCHEDULE_OVERLAPPED);
        }
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setStartAt(request.startAt());
        schedule.setEndAt(request.endAt());
        for (LocalDateTime cursor = request.startAt(); cursor.isBefore(request.endAt()); cursor = cursor.plusMinutes(SLOT_MINUTES)) {
            ScheduleSlot slot = new ScheduleSlot();
            slot.setStartAt(cursor);
            slot.setEndAt(cursor.plusMinutes(SLOT_MINUTES));
            schedule.addSlot(slot);
        }
        return toResponse(repository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> mySchedules() {
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        return repository.findAllByDoctorIdAndActiveTrueOrderByStartAtAsc(doctor.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deactivate(Long id) {
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        DoctorSchedule schedule = repository.lockOwnedSchedule(id, doctor.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        if (slotRepository.existsByScheduleIdAndStatus(schedule.getId(), SlotStatus.BOOKED)) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        schedule.setActive(false);
        schedule.getSlots().forEach(slot -> slot.setStatus(SlotStatus.BLOCKED));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> availableSlots(Long doctorId, LocalDate date) {
        doctorService.getActive(doctorId);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        if (from.isBefore(now)) from = now;
        return slotRepository.findAllByDoctorIdAndStatusAndStartAtBetweenOrderByStartAtAsc(
                doctorId, SlotStatus.AVAILABLE, from, to).stream().map(this::toSlotResponse).toList();
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        long minutes = ChronoUnit.MINUTES.between(start, end);
        boolean aligned = start.getMinute() % SLOT_MINUTES == 0 && end.getMinute() % SLOT_MINUTES == 0
                && start.getSecond() == 0 && end.getSecond() == 0;
        if (!start.toLocalDate().equals(end.toLocalDate()) || !start.isAfter(LocalDateTime.now())
                || minutes < SLOT_MINUTES || minutes % SLOT_MINUTES != 0 || !aligned) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
    }

    private ScheduleResponse toResponse(DoctorSchedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getStartAt(), schedule.getEndAt(),
                schedule.getSlots().stream().map(this::toSlotResponse).toList());
    }

    private SlotResponse toSlotResponse(ScheduleSlot slot) {
        return new SlotResponse(slot.getId(), slot.getStartAt(), slot.getEndAt());
    }
}
