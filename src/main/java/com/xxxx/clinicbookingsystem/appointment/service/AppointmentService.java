package com.xxxx.clinicbookingsystem.appointment.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentResponse;
import com.xxxx.clinicbookingsystem.appointment.dto.CreateAppointmentRequest;
import com.xxxx.clinicbookingsystem.appointment.dto.UpdateAppointmentStatusRequest;
import com.xxxx.clinicbookingsystem.appointment.dto.AppointmentReasonRequest;
import com.xxxx.clinicbookingsystem.appointment.dto.DoctorAppointmentSummary;
import com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordRequest;
import com.xxxx.clinicbookingsystem.medical.entity.MedicalRecord;
import com.xxxx.clinicbookingsystem.medical.repository.MedicalRecordRepository;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import static com.xxxx.clinicbookingsystem.notification.entity.NotificationType.*;
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
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumMap;

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
    private final Clock clock;
    private final ScheduleRepository scheduleRepository;
    private final com.xxxx.clinicbookingsystem.schedule.planning.DoctorLeaveRepository leaveRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public DoctorAppointmentSummary doctorSummary() {
        Long doctorId = doctorService.getByAccountId(currentAccount.id()).getId();
        var counts = new EnumMap<AppointmentStatus, Long>(AppointmentStatus.class);
        // Đếm toàn bộ lịch hẹn, không suy ra thống kê từ một trang kết quả.
        repository.countStatuses(doctorId).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return new DoctorAppointmentSummary(
                counts.values().stream().mapToLong(Long::longValue).sum(),
                counts.getOrDefault(AppointmentStatus.PENDING, 0L),
                counts.getOrDefault(AppointmentStatus.CONFIRMED, 0L),
                counts.getOrDefault(AppointmentStatus.COMPLETED, 0L),
                scheduleRepository.countByDoctorIdAndActiveTrueAndEndAtGreaterThan(doctorId, now()));
    }

    @Transactional
    public AppointmentResponse book(CreateAppointmentRequest request) {
        // Khóa bệnh nhân trước bác sĩ để hai booking khác bác sĩ vẫn không trùng giờ.
        Account patient = accountRepository.lockById(currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!Boolean.TRUE.equals(patient.getIsActive())) throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        var target = slotRepository.findBookingTarget(request.slotId())
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));

        Doctor doctor = doctorRepository.lockById(target.getDoctorId())
                .orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));

        // Đọc ca bằng khóa để thấy lần hủy vừa commit, kể cả khi transaction đã có snapshot cũ.
        var schedule = scheduleRepository.lockById(target.getScheduleId())
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE));

        ScheduleSlot slot = slotRepository.lockById(request.slotId())
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        if (slot.getStatus() != SlotStatus.AVAILABLE || !slot.getStartAt().isAfter(now())
                || !Boolean.TRUE.equals(schedule.getActive())) {
            throw new AppException(ErrorCode.SLOT_ALREADY_BOOKED);
        }

        doctorService.requireAvailable(doctor);
        if (!leaveRepository.lockBlocks(doctor.getId(), slot.getStartAt(), slot.getEndAt()).isEmpty()) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        if (!repository.findOverlappingAppointments(patient.getId(),
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                slot.getStartAt(), slot.getEndAt()).isEmpty()) {
            throw new AppException(ErrorCode.PATIENT_SCHEDULE_CONFLICT);
        }
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING);
        slot.setStatus(SlotStatus.BOOKED);
        repository.save(appointment);
        notificationService.appointmentChanged(appointment, APPOINTMENT_BOOKED);
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> mine(int page, int size) {
        return mine(page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> mine(int page, int size, AppointmentStatus status) {
        return toPageResponse(repository.findFiltered(currentAccount.id(), null, status, pageable(page, size)));
    }

    @Transactional
    public AppointmentResponse cancel(Long id) {
        Appointment appointment = lockAppointment(id);
        if (!appointment.getPatient().getId().equals(currentAccount.id())
                || !(appointment.getStatus() == AppointmentStatus.PENDING || appointment.getStatus() == AppointmentStatus.CONFIRMED)
                || !appointment.getSlot().getStartAt().isAfter(now())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointment.setStatusReason("Bệnh nhân hủy lịch");
        releaseSlot(appointment);
        notificationService.appointmentChanged(appointment, APPOINTMENT_CANCELED);
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> doctorAppointments(int page, int size) {
        return doctorAppointments(page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> doctorAppointments(int page, int size, AppointmentStatus status) {
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        var appointments = repository.findFiltered(null, doctor.getId(), status, pageable(page, size));
        return toPageResponse(appointments);
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, UpdateAppointmentStatusRequest request) {
        Appointment appointment = lockAppointment(id);
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        doctorService.requireAvailable(doctor);
        LocalDateTime now = now();
        boolean confirm = appointment.getStatus() == AppointmentStatus.PENDING
                && request.status() == AppointmentStatus.CONFIRMED && now.isBefore(appointment.getSlot().getStartAt());
        boolean complete = appointment.getStatus() == AppointmentStatus.CONFIRMED
                && request.status() == AppointmentStatus.COMPLETED && !now.isBefore(appointment.getSlot().getStartAt());
        boolean noShow = appointment.getStatus() == AppointmentStatus.CONFIRMED
                && request.status() == AppointmentStatus.NO_SHOW && !now.isBefore(appointment.getSlot().getEndAt());
        if (!appointment.getDoctor().getId().equals(doctor.getId()) || (!confirm && !complete && !noShow)) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        if (complete && !medicalRecordRepository.existsByAppointmentId(id)) {
            throw new AppException(ErrorCode.MEDICAL_RECORD_REQUIRED);
        }
        appointment.setStatus(request.status());
        if (complete) appointment.getSlot().setStatus(SlotStatus.BLOCKED);
        if (noShow) {
            appointment.setStatusReason("Bác sĩ ghi nhận bệnh nhân vắng mặt");
            appointment.getSlot().setStatus(SlotStatus.BLOCKED);
        }
        notificationService.appointmentChanged(appointment,
                confirm ? APPOINTMENT_CONFIRMED : complete ? APPOINTMENT_COMPLETED : APPOINTMENT_NO_SHOW);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long id, MedicalRecordRequest request) {
        Appointment appointment = lockAppointment(id);
        Doctor doctor = doctorService.getByAccountId(currentAccount.id());
        doctorService.requireAvailable(doctor);
        if (!appointment.getDoctor().getId().equals(doctor.getId())
                || appointment.getStatus() != AppointmentStatus.CONFIRMED
                || now().isBefore(appointment.getSlot().getStartAt())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        if (request.diagnosis() == null || request.diagnosis().isBlank()
                || request.diagnosis().length() > 5000) throw new AppException(ErrorCode.INVALID_REQUEST);
        if (medicalRecordRepository.existsByAppointmentId(id)) throw new AppException(ErrorCode.RESOURCE_CONFLICT);
        var record = new MedicalRecord();
        record.setAppointment(appointment);
        record.setDiagnosis(request.diagnosis().trim());
        record.setExaminationNotes(cleanOptional(request.examinationNotes()));
        record.setTreatmentAdvice(cleanOptional(request.treatmentAdvice()));
        // Kết quả, trạng thái và thông báo phải cùng commit; lỗi ở đâu thì hủy cả lần hoàn thành.
        medicalRecordRepository.save(record);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.getSlot().setStatus(SlotStatus.BLOCKED);
        notificationService.appointmentChanged(appointment, APPOINTMENT_COMPLETED);
        return toResponse(appointment);
    }

    private String cleanOptional(String value) {
        if (value != null && value.length() > 5000) throw new AppException(ErrorCode.INVALID_REQUEST);
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> adminAppointments(int page, int size, AppointmentStatus status, Long doctorId) {
        return toPageResponse(repository.findFiltered(null, doctorId, status, pageable(page, size)));
    }

    @Transactional
    public AppointmentResponse adminCancel(Long id, AppointmentReasonRequest request) {
        Appointment appointment = lockAppointment(id);
        if (!isOpen(appointment) || !appointment.getSlot().getStartAt().isAfter(now())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointment.setStatusReason(reason(request));
        releaseSlot(appointment);
        notificationService.appointmentChanged(appointment, APPOINTMENT_CANCELED);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse adminNoShow(Long id, AppointmentReasonRequest request) {
        Appointment appointment = lockAppointment(id);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED || now().isBefore(appointment.getSlot().getEndAt())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointment.setStatusReason(reason(request));
        appointment.getSlot().setStatus(SlotStatus.BLOCKED);
        notificationService.appointmentChanged(appointment, APPOINTMENT_NO_SHOW);
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<Long> duePendingIds() {
        return repository.findDueIds(AppointmentStatus.PENDING, now(), PageRequest.of(0, 100));
    }

    @Transactional
    public void expirePending(Long id) {
        Appointment appointment = lockAppointment(id);
        // Kiểm tra lại sau khi có khóa: bác sĩ có thể vừa xác nhận trước lượt job này.
        if (appointment.getStatus() == AppointmentStatus.PENDING && !now().isBefore(appointment.getSlot().getEndAt())) {
            appointment.setStatus(AppointmentStatus.EXPIRED);
            appointment.setStatusReason("Hết giờ khám nhưng chưa được xác nhận");
            appointment.getSlot().setStatus(SlotStatus.BLOCKED);
            notificationService.appointmentChanged(appointment, APPOINTMENT_EXPIRED);
        }
    }

    private Appointment lockAppointment(Long id) {
        var target = repository.findTargetById(id)
                .orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
        // Cùng thứ tự với book; không giữ khóa lịch hẹn rồi quay lại chờ khóa bệnh nhân.
        accountRepository.lockById(target.getPatientId()).orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        doctorRepository.lockById(target.getDoctorId()).orElseThrow(() -> new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        scheduleRepository.lockById(target.getScheduleId()).orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        slotRepository.lockById(target.getSlotId()).orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_FOUND));
        return repository.lockById(id).orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }

    private void releaseSlot(Appointment appointment) {
        boolean reusable = Boolean.TRUE.equals(appointment.getSlot().getSchedule().getActive())
                && appointment.getSlot().getStartAt().isAfter(now());
        appointment.getSlot().setStatus(reusable ? SlotStatus.AVAILABLE : SlotStatus.BLOCKED);
    }

    private boolean isOpen(Appointment appointment) {
        return appointment.getStatus() == AppointmentStatus.PENDING || appointment.getStatus() == AppointmentStatus.CONFIRMED;
    }

    private String reason(AppointmentReasonRequest request) {
        String value = request.reason().trim();
        if (value.isEmpty() || value.length() > 500) throw new AppException(ErrorCode.INVALID_REQUEST);
        return value;
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "slot.startAt", "id"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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
                patient.getPhone(),
                appointment.getStatusReason()
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
