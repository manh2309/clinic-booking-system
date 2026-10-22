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
    private final Clock clock;
    private final com.xxxx.clinicbookingsystem.schedule.planning.DoctorLeaveRepository leaveRepository;

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        validateTime(request.startAt(), request.endAt());
        Doctor doctor = doctorRepository.lockByAccountId(currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        doctorService.requireAvailable(doctor);
        if (repository.existsByDoctorIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(
                doctor.getId(), request.endAt(), request.startAt())) {
            throw new AppException(ErrorCode.SCHEDULE_OVERLAPPED);
        }
        // Slot cũ còn được lịch sử tham chiếu; khôi phục ca thay vì chèn slot trùng.
        if (repository.existsByDoctorIdAndActiveFalseAndStartAtLessThanAndEndAtGreaterThan(
                doctor.getId(), request.endAt(), request.startAt())) {
            throw new AppException(ErrorCode.SCHEDULE_RESTORE_REQUIRED);
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
        var blocks = blockingLeaves(doctor.getId());
        return repository.findAllByDoctorIdOrderByStartAtDesc(doctor.getId()).stream().map(schedule -> toResponse(schedule, blocks)).toList();
    }

    @Transactional
    public void deactivate(Long id) {
        DoctorSchedule schedule = lockOwnFutureSchedule(id);
        if (!Boolean.TRUE.equals(schedule.getActive())) return;
        if (slotRepository.existsByScheduleIdAndStatus(schedule.getId(), SlotStatus.BOOKED)) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        schedule.setActive(false);
        schedule.getSlots().forEach(slot -> slot.setStatus(SlotStatus.BLOCKED));
    }

    @Transactional
    public ScheduleResponse restore(Long id) {
        DoctorSchedule schedule = lockOwnFutureSchedule(id);
        if (Boolean.TRUE.equals(schedule.getActive())) return toResponse(schedule);
        if (repository.existsByDoctorIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(
                schedule.getDoctor().getId(), schedule.getEndAt(), schedule.getStartAt())) {
            throw new AppException(ErrorCode.SCHEDULE_OVERLAPPED);
        }
        if (slotRepository.existsByScheduleIdAndStatus(id, SlotStatus.BOOKED)) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        schedule.setActive(true);
        schedule.getSlots().forEach(slot -> slot.setStatus(SlotStatus.AVAILABLE));
        return toResponse(schedule);
    }

    private DoctorSchedule lockOwnFutureSchedule(Long id) {
        // Booking cũng giữ khóa doctor trước slot; hủy/khôi phục ca phải đi cùng thứ tự.
        Doctor doctor = doctorRepository.lockByAccountId(currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        doctorService.requireAvailable(doctor);
        DoctorSchedule schedule = repository.lockOwnedSchedule(id, doctor.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        if (!schedule.getStartAt().isAfter(LocalDateTime.now(clock))) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        return schedule;
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> availableSlots(Long doctorId, LocalDate date) {
        doctorService.getActive(doctorId);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock);
        return slotRepository.findAvailable(doctorId, SlotStatus.AVAILABLE, from, to, now)
                .stream().map(this::toSlotResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableDateResponse> availableDates(Long doctorId, LocalDate from, int days) {
        var today = LocalDate.now(clock);
        if (days < 1 || days > 90 || from == null || from.isBefore(today) || from.isAfter(today.plusYears(1))) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        doctorService.getActive(doctorId);
        return slotRepository.findAvailable(doctorId, SlotStatus.AVAILABLE, from.atStartOfDay(),
                        from.plusDays(days).atStartOfDay(), LocalDateTime.now(clock)).stream()
                .collect(java.util.stream.Collectors.groupingBy(slot -> slot.getStartAt().toLocalDate(),
                        java.util.TreeMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream().map(entry -> new AvailableDateResponse(entry.getKey(), entry.getValue())).toList();
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        long minutes = ChronoUnit.MINUTES.between(start, end);
        boolean aligned = start.getMinute() % SLOT_MINUTES == 0 && end.getMinute() % SLOT_MINUTES == 0
                && start.getSecond() == 0 && end.getSecond() == 0 && start.getNano() == 0 && end.getNano() == 0;
        if (!start.toLocalDate().equals(end.toLocalDate()) || !start.isAfter(LocalDateTime.now(clock))
                || minutes < SLOT_MINUTES || minutes % SLOT_MINUTES != 0 || !aligned) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
    }

    private List<com.xxxx.clinicbookingsystem.schedule.planning.DoctorLeave> blockingLeaves(Long doctorId) {
        return leaveRepository.list(doctorId).stream()
                .filter(leave -> leave.getStatus().equals("PENDING") || leave.getStatus().equals("APPROVED")).toList();
    }

    private ScheduleResponse toResponse(DoctorSchedule schedule) {
        return toResponse(schedule, blockingLeaves(schedule.getDoctor().getId()));
    }

    private ScheduleResponse toResponse(DoctorSchedule schedule, List<com.xxxx.clinicbookingsystem.schedule.planning.DoctorLeave> leaves) {
        return new ScheduleResponse(schedule.getId(), schedule.getStartAt(), schedule.getEndAt(),
                schedule.getSlots().stream().map(slot -> {
                    var leave = leaves.stream().filter(l -> l.getStartAt().isBefore(slot.getEndAt()) && l.getEndAt().isAfter(slot.getStartAt())).findFirst();
                    String reason = slot.getStatus() != SlotStatus.BOOKED && leave.isPresent()
                            ? leave.get().getStatus().equals("PENDING") ? "PENDING_LEAVE" : "APPROVED_LEAVE" : null;
                    return new SlotResponse(slot.getId(), slot.getStartAt(), slot.getEndAt(),
                            reason == null ? slot.getStatus() : SlotStatus.BLOCKED, reason);
                }).toList(), schedule.getActive(), Boolean.TRUE.equals(schedule.getDoctor().getActive())
                && Boolean.TRUE.equals(schedule.getDoctor().getAccount().getIsActive())
                && Boolean.TRUE.equals(schedule.getDoctor().getSpecialty().getActive()));
    }

    private SlotResponse toSlotResponse(ScheduleSlot slot) {
        return new SlotResponse(slot.getId(), slot.getStartAt(), slot.getEndAt(), slot.getStatus(), null);
    }
}
