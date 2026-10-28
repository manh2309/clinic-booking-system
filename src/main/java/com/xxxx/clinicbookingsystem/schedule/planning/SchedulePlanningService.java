package com.xxxx.clinicbookingsystem.schedule.planning;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.doctor.service.DoctorService;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import com.xxxx.clinicbookingsystem.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.xxxx.clinicbookingsystem.schedule.planning.PlanningDtos.*;
@Service @RequiredArgsConstructor
public class SchedulePlanningService  {
    private final WeeklyScheduleRepository weekly;
    private final DoctorLeaveRepository leaves;
    private final LeaveContactRepository contacts;
    private final DoctorRepository doctors;
    private final com.xxxx.clinicbookingsystem.account.repository.AccountRepository accounts;
    private final DoctorService doctorService;
    private final ScheduleRepository schedules;
    private final ScheduleSlotRepository slots;
    private final AppointmentRepository appointments;
    private final NotificationService notifications;
    private final CurrentAccount current;
    private final Clock clock;
    private final com.xxxx.clinicbookingsystem.appointment.visit.VisitService visitService;
    @Value("${clinic.schedules.horizon-days:30}") private int horizonDays;
    @Transactional(readOnly=true)
    public List<WeeklyResponse> weekly(Long doctorId)  {
        return weekly.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream().map(this::weeklyResponse).toList();
    }
    @Transactional
    public WeeklyResponse addWeekly(Long doctorId, WeeklyRequest r)  {
        var doctor=lockDoctor(doctorId);
        doctorService.requireAvailable(doctor);
        if (r.dayOfWeek()<1 || r.dayOfWeek()>7 || r.effectiveFrom().isBefore(today()) || r.effectiveTo().isBefore(r.effectiveFrom())
        || r.effectiveTo().isAfter(r.effectiveFrom().plusYears(1)) || !r.startTime().isBefore(r.endTime())
        || !aligned(r.startTime()) || !aligned(r.endTime())) throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        var existing=weekly.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream()
        .filter(w->w.isActive() && w.getDayOfWeek()==r.dayOfWeek() && !w.getEffectiveFrom().isAfter(r.effectiveTo()) && !w.getEffectiveTo().isBefore(r.effectiveFrom())).toList();
        if (existing.stream().anyMatch(w->w.getStartTime().isBefore(r.endTime()) && w.getEndTime().isAfter(r.startTime()))) throw new AppException(ErrorCode.WEEKLY_SCHEDULE_CONFLICT);
        var boundaries=new HashSet<LocalDate>();
        boundaries.add(r.effectiveFrom());
        existing.forEach(w->boundaries.add(w.getEffectiveFrom().isBefore(r.effectiveFrom())?r.effectiveFrom():w.getEffectiveFrom()));
        long added=ChronoUnit.MINUTES.between(r.startTime(),r.endTime());
        for(var date:boundaries)  {
            long total=added+existing.stream().filter(w->!date.isBefore(w.getEffectiveFrom())&&!date.isAfter(w.getEffectiveTo()))
            .mapToLong(w->ChronoUnit.MINUTES.between(w.getStartTime(),w.getEndTime())).sum();
            if(total>480) throw new AppException(ErrorCode.WEEKLY_SCHEDULE_CONFLICT);
        }
        var w=new WeeklySchedule();
        w.setDoctor(doctor);
        w.setDayOfWeek(r.dayOfWeek());
        w.setStartTime(r.startTime());
        w.setEndTime(r.endTime());
        w.setEffectiveFrom(r.effectiveFrom());
        w.setEffectiveTo(r.effectiveTo());
        weekly.save(w);
        return weeklyResponse(w);
    }
    @Transactional
    public WeeklyBatchResult addWeeklyBatch(Long doctorId, WeeklyBatchRequest request) {
        var doctor = lockDoctor(doctorId);
        doctorService.requireAvailable(doctor);
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty() || request.daysOfWeek().size() > 7
                || request.windows() == null || request.windows().isEmpty() || request.windows().size() > 8) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        int added = 0;
        int skipped = 0;
        for (int day : new TreeSet<>(request.daysOfWeek())) {
            for (var window : request.windows().stream().distinct().toList()) {
                var existing = weekly.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
                boolean identical = existing.stream().anyMatch(w -> w.isActive() && w.getDayOfWeek() == day
                        && w.getStartTime().equals(window.startTime()) && w.getEndTime().equals(window.endTime())
                        && w.getEffectiveFrom().equals(request.effectiveFrom()) && w.getEffectiveTo().equals(request.effectiveTo()));
                if (identical) {
                    skipped++;
                    continue;
                }
                try {
                    addWeekly(doctorId, new WeeklyRequest(day, window.startTime(), window.endTime(),
                            request.effectiveFrom(), request.effectiveTo()));
                    added++;
                } catch (AppException e) {
                    String dayLabel = day == 7 ? "Chủ nhật" : "Thứ " + (day + 1);
                    throw new WeeklyBatchConflict(dayLabel + " " + window.startTime() + "–" + window.endTime()
                            + ": " + e.getErrorCode().getMessage() + ". Chưa lưu thay đổi nào trong lượt này.");
                }
            }
        }
        var generated = generate(doctorId);
        return new WeeklyBatchResult(added, skipped, generated.created(), generated.conflicts());
    }

    @Transactional
    public void endWeekly(Long doctorId,Long id,LocalDate from)  {
        lockDoctor(doctorId);
        if(from.isBefore(today().plusDays(1)))throw new AppException(ErrorCode.INVALID_REQUEST);
        var w=weekly.findById(id).filter(x->x.getDoctor().getId().equals(doctorId)).orElseThrow(()->new AppException(ErrorCode.INVALID_REQUEST));
        if(!w.isActive())return;
        var future=schedules.lockRange(doctorId,from.atStartOfDay(),w.getEffectiveTo().plusDays(1).atStartOfDay()).stream().filter(x->id.equals(x.getWeeklyScheduleId())&&Boolean.TRUE.equals(x.getActive())).toList();
        if(future.stream().anyMatch(x->slots.existsByScheduleIdAndStatus(x.getId(),SlotStatus.BOOKED)))throw new AppException(ErrorCode.LEAVE_CANCELLATION_REQUIRED);
        for(var ca:future) {
            ca.setActive(false);
            ca.getSlots().forEach(slot->slot.setStatus(SlotStatus.BLOCKED));
        }
        if(!from.isAfter(w.getEffectiveFrom()))w.setActive(false);
        else if(!from.isAfter(w.getEffectiveTo()))w.setEffectiveTo(from.minusDays(1));
    }
    @Transactional
    public GenerationResult generate(Long doctorId)  {
        var doctor=lockDoctor(doctorId);
        if(!Boolean.TRUE.equals(doctor.getActive()) || !Boolean.TRUE.equals(doctor.getAccount().getIsActive()) || !Boolean.TRUE.equals(doctor.getSpecialty().getActive())) return new GenerationResult(0,List.of("Bác sĩ chưa hoạt động"));
        var rules=weekly.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream().filter(WeeklySchedule::isActive).toList();
        int created=0;
        var conflicts=new ArrayList<String>();
        for(var date=today();
        date.isBefore(today().plusDays(Math.max(1,Math.min(horizonDays,90))));
        date=date.plusDays(1))  {
            for(var w:rules)  {
                if(w.getDayOfWeek()!=date.getDayOfWeek().getValue()||date.isBefore(w.getEffectiveFrom())||date.isAfter(w.getEffectiveTo()))continue;
                var start=date.atTime(w.getStartTime());
                var end=date.atTime(w.getEndTime());
                if(!end.isAfter(now()))continue;
                var overlapping=schedules.lockRange(doctorId,start,end);
                if(!overlapping.isEmpty())  {
                    if(overlapping.size()!=1 || !overlapping.get(0).getStartAt().equals(start)||!overlapping.get(0).getEndAt().equals(end)||!Boolean.TRUE.equals(overlapping.get(0).getActive())) conflicts.add(start+" — đã có ca khác hoặc ca đã hủy; giữ nguyên");
                    continue;
                }
                var schedule=new DoctorSchedule();
                schedule.setDoctor(doctor);
                schedule.setWeeklyScheduleId(w.getId());
                schedule.setStartAt(start);
                schedule.setEndAt(end);
                for(var time=start;
                time.isBefore(end);
                time=time.plusMinutes(30))  {
                    var slot=new ScheduleSlot();
                    slot.setStartAt(time);
                    slot.setEndAt(time.plusMinutes(30));
                    schedule.addSlot(slot);
                }
                schedules.saveAndFlush(schedule);
                created++;
            }
        }
        return new GenerationResult(created,conflicts);
    }
    @Transactional(readOnly=true)
    public List<LeaveResponse> listLeaves(Long doctorId)  {
        return leaves.list(doctorId).stream().map(this::leaveResponse).toList();
    }
    @Transactional(readOnly=true)
    public List<LeaveResponse> mine()  {
        return listLeaves(doctorService.getByAccountId(current.id()).getId());
    }
    @Transactional
    public LeaveResponse request(LeaveRequest r)  {
        var doctor=doctors.lockByAccountId(current.id()).orElseThrow(()->new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        doctorService.requireAvailable(doctor);
        if(!r.startAt().isAfter(now())||!r.startAt().isBefore(r.endAt())||r.endAt().isAfter(r.startAt().plusDays(31))||!aligned(r.startAt().toLocalTime())||!aligned(r.endAt().toLocalTime())||r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw new AppException(ErrorCode.INVALID_REQUEST);
        if(!leaves.lockBlocks(doctor.getId(),r.startAt(),r.endAt()).isEmpty())throw new AppException(ErrorCode.RESOURCE_CONFLICT);
        var l=new DoctorLeave();
        l.setDoctor(doctor);
        l.setStartAt(r.startAt());
        l.setEndAt(r.endAt());
        l.setReason(r.reason().trim());
        leaves.save(l);
        return leaveResponse(l);
    }
    @Transactional
    public LeaveResponse withdraw(Long id)  {
        var doctor=doctors.lockByAccountId(current.id()).orElseThrow(()->new AppException(ErrorCode.DOCTOR_NOT_FOUND));
        var l=leaves.lockById(id).filter(x->x.getDoctor().getId().equals(doctor.getId())).orElseThrow(()->new AppException(ErrorCode.LEAVE_NOT_FOUND));
        if(!l.getStatus().equals("PENDING"))throw new AppException(ErrorCode.LEAVE_REVIEW_CONFLICT);
        l.setStatus("WITHDRAWN");
        return leaveResponse(l);
    }
    @Transactional(readOnly=true)
    public List<Affected> affected(Long id)  {
        var l=leaves.findById(id).orElseThrow(()->new AppException(ErrorCode.LEAVE_NOT_FOUND));
        if(l.getStatus().equals("APPROVED"))return contacts.forLeave(id).stream().map(c->affectedResponse(c.getAppointment(),c)).toList();
        return appointments.affected(l.getDoctor().getId(),l.getStartAt(),l.getEndAt()).stream().map(a->affectedResponse(a,null)).toList();
    }
    @Transactional
    public LeaveResponse review(Long id,ReviewRequest r)  {
        var range=leaves.range(id).orElseThrow(()->new AppException(ErrorCode.LEAVE_NOT_FOUND));
        Long doctorId=range.getDoctorId();
        var patientIds=new TreeSet<>(appointments.affectedPatientIds(doctorId,range.getStartAt(),range.getEndAt()));
        patientIds.forEach(patientId->accounts.lockById(patientId).orElseThrow(()->new AppException(ErrorCode.ACCOUNT_NOT_FOUND)));
        lockDoctor(doctorId);
        var l=leaves.lockById(id).orElseThrow(()->new AppException(ErrorCode.LEAVE_NOT_FOUND));
        if(!l.getStatus().equals("PENDING"))throw new AppException(ErrorCode.LEAVE_REVIEW_CONFLICT);
        if(r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw new AppException(ErrorCode.INVALID_REQUEST);
        if(r.decision().equals("APPROVED"))  {
            if(!l.getEndAt().isAfter(now()))throw new AppException(ErrorCode.LEAVE_REVIEW_CONFLICT);
            schedules.lockRange(doctorId,l.getStartAt(),l.getEndAt());
            slots.lockRange(doctorId,l.getStartAt(),l.getEndAt());
            var affected=appointments.lockAffected(doctorId,l.getStartAt(),l.getEndAt());
            if(affected.stream().anyMatch(a->!patientIds.contains(a.getPatient().getId())))throw new AppException(ErrorCode.LEAVE_REVIEW_CONFLICT);
            if(affected.stream().anyMatch(a->!a.getSlot().getStartAt().isAfter(now())))throw new AppException(ErrorCode.LEAVE_REVIEW_CONFLICT);
            if(!affected.isEmpty()&&!r.confirmCancellation())throw new AppException(ErrorCode.LEAVE_CANCELLATION_REQUIRED);
            for(var a:affected)  {
                visitService.requireNotReceived(a.getId());
                a.setStatus(AppointmentStatus.CANCELED);
                a.setStatusReason("Bác sĩ nghỉ — "+r.reason().trim().substring(0,Math.min(480,r.reason().trim().length())));
                a.getSlot().setStatus(SlotStatus.BLOCKED);
                var c=new LeaveContact();
                c.setLeave(l);
                c.setAppointment(a);
                contacts.save(c);
                notifications.appointmentChanged(a,NotificationType.APPOINTMENT_CANCELED);
            }
        }
        else if(!r.decision().equals("REJECTED"))throw new AppException(ErrorCode.INVALID_REQUEST);
        l.setStatus(r.decision());
        l.setReviewedBy(current.id());
        l.setReviewedAt(now());
        l.setReviewReason(r.reason().trim());
        return leaveResponse(l);
    }
    @Transactional
    public void contact(Long id,Long contactId)  {
        Long doctorId=leaves.doctorId(id).orElseThrow(()->new AppException(ErrorCode.LEAVE_NOT_FOUND));
        lockDoctor(doctorId);
        var c=contacts.findById(contactId).filter(x->x.getLeave().getId().equals(id)).orElseThrow(()->new AppException(ErrorCode.LEAVE_NOT_FOUND));
        if(c.getContactedAt()==null) {
            c.setContactedAt(now());
            c.setContactedBy(current.id());
        }
    }
    private Doctor lockDoctor(Long id) {
        return doctors.lockById(id).orElseThrow(()->new AppException(ErrorCode.DOCTOR_NOT_FOUND));
    }
    private boolean aligned(LocalTime t) {
        return t.getMinute()%30==0&&t.getSecond()==0&&t.getNano()==0;
    }
    private LocalDate today() {
        return LocalDate.now(clock);
    }
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
    private WeeklyResponse weeklyResponse(WeeklySchedule w) {
        return new WeeklyResponse(w.getId(),w.getDayOfWeek(),w.getStartTime(),w.getEndTime(),w.getEffectiveFrom(),w.getEffectiveTo(),w.isActive());
    }
    private LeaveResponse leaveResponse(DoctorLeave l) {
        return new LeaveResponse(l.getId(),l.getDoctor().getId(),l.getDoctor().getFullName(),l.getStartAt(),l.getEndAt(),l.getReason(),l.getStatus(),l.getReviewedBy(),l.getReviewedAt(),l.getReviewReason());
    }
    private Affected affectedResponse(Appointment a,LeaveContact c) {
        return new Affected(a.getId(),a.getPatient().getId(),a.getPatient().getUsername(),a.getPatient().getPhone(),a.getSlot().getStartAt(),a.getStatus().name(),c==null?null:c.getId(),c==null?null:c.getContactedAt());
    }
}
