package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.appointment.visit.*;
import com.xxxx.clinicbookingsystem.appointment.reschedule.*;
import com.xxxx.clinicbookingsystem.auth.jwt.JwtService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordRequest;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
import com.xxxx.clinicbookingsystem.schedule.planning.*;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(AppointmentLifecycleIntegrationTests.Time.class)
class VisitIntegrationTests {
    @Autowired VisitService service;
    @Autowired VisitRepository visits;
    @Autowired VisitEventRepository events;
    @Autowired AppointmentService booking;
    @Autowired AppointmentRepository appointments;
    @Autowired RescheduleService reschedule;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired DoctorRepository doctors;
    @Autowired SpecialtyRepository specialties;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleSlotRepository slots;
    @Autowired DoctorLeaveRepository leaves;
    @Autowired SchedulePlanningService planning;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoSpyBean NotificationService notifications;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void receptionAndStartAreIdempotentAndCompletionStoresVisitWithMedicalRecord() throws Exception {
        var f = fixture(now().minusMinutes(10)); authenticate(account("ADMIN"));
        var first = service.checkIn(f.a().getId());
        assertThat(service.checkIn(f.a().getId()).id()).isEqualTo(first.id());
        assertThat(events.findByVisitIdOrderByIdAsc(first.id())).hasSize(1);
        authenticate(f.doctor().getAccount());
        service.start(f.a().getId()); service.start(f.a().getId());
        assertThat(events.findByVisitIdOrderByIdAsc(first.id())).hasSize(2);
        var completed = booking.complete(f.a().getId(), new MedicalRecordRequest("Kết quả kiểm thử", null, null));
        assertThat(completed.visit().status()).isEqualTo(ClinicVisit.Status.COMPLETED);
        assertThat(visits.findByActiveDoctorId(f.doctor().getId())).isEmpty();
        assertThat(events.findByVisitIdOrderByIdAsc(first.id())).extracting(VisitEvent::getAction).containsExactly("CHECKED_IN","STARTED","COMPLETED");
        mvc.perform(get("/api/v1/appointments/me").header("Authorization", token(f.a().getPatient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].visit.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/patient/me/medical-records/" + f.a().getId()).header("Authorization", token(f.a().getPatient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diagnosis").value("Kết quả kiểm thử"));
    }

    @Test void completionCannotSkipReceptionOrStart() {
        var f = fixture(now().minusMinutes(10)); authenticate(f.doctor().getAccount());
        expect(ErrorCode.VISIT_REQUIRED, () -> booking.complete(f.a().getId(), new MedicalRecordRequest("Kết quả",null,null)));
        authenticate(account("ADMIN")); service.checkIn(f.a().getId()); authenticate(f.doctor().getAccount());
        expect(ErrorCode.VISIT_REQUIRED, () -> booking.complete(f.a().getId(), new MedicalRecordRequest("Kết quả",null,null)));
    }

    @Test void receptionRequiresConfirmedActiveAppointmentAndValidSameDayWindow() {
        authenticate(account("ADMIN"));
        for (LocalDateTime time : List.of(now().plusMinutes(31), now().minusMinutes(91), now().minusDays(1))) {
            var f = fixture(time); expect(ErrorCode.VISIT_TIME_INVALID, () -> service.checkIn(f.a().getId()));
        }
        var earlyBoundary = fixture(now().plusMinutes(30)); service.checkIn(earlyBoundary.a().getId());
        var lateBoundary = fixture(now().minusMinutes(90)); service.checkIn(lateBoundary.a().getId());
        var pending = fixture(now()); pending.a().setStatus(AppointmentStatus.PENDING); appointments.saveAndFlush(pending.a());
        expect(ErrorCode.INVALID_APPOINTMENT_STATUS, () -> service.checkIn(pending.a().getId()));
        var inactive = fixture(now()); inactive.doctor().setActive(false); doctors.saveAndFlush(inactive.doctor());
        expect(ErrorCode.DOCTOR_NOT_AVAILABLE, () -> service.checkIn(inactive.a().getId()));
    }

    @Test void earlyReceptionDoesNotAllowStartingBeforeBookedTime() {
        var f = fixture(now().plusMinutes(20)); authenticate(account("ADMIN")); service.checkIn(f.a().getId());
        authenticate(f.doctor().getAccount());
        expect(ErrorCode.INVALID_APPOINTMENT_STATUS, () -> service.start(f.a().getId()));
    }

    @Test void waitingReceptionMustBeWithdrawnBeforeCancellationOrRescheduling() {
        var f = fixture(now().plusMinutes(20)); var admin = account("ADMIN"); authenticate(admin); service.checkIn(f.a().getId());
        authenticate(f.a().getPatient()); expect(ErrorCode.VISIT_ALREADY_RECEIVED, () -> booking.cancel(f.a().getId()));
        authenticate(admin);
        expect(ErrorCode.VISIT_ALREADY_RECEIVED, () -> booking.adminCancel(f.a().getId(), new AppointmentReasonRequest("Hủy")));
        var target = slot(f.doctor(), now().plusDays(1));
        expect(ErrorCode.VISIT_ALREADY_RECEIVED, () -> reschedule.propose(f.a().getId(), new RescheduleDtos.Create(target.getId(),"Đổi",now().plusMinutes(5))));
        var canceled = service.cancelReception(f.a().getId(), new AppointmentReasonRequest("Bệnh nhân rời phòng khám"));
        service.checkIn(f.a().getId());
        assertThat(events.findByVisitIdOrderByIdAsc(canceled.id())).extracting(VisitEvent::getAction).containsExactly("CHECKED_IN","CANCELED","CHECKED_IN");
        service.cancelReception(f.a().getId(), new AppointmentReasonRequest("Rút tiếp nhận để đổi giờ"));
        assertThat(reschedule.propose(f.a().getId(), new RescheduleDtos.Create(target.getId(),"Đổi",now().plusMinutes(5))).status()).isEqualTo(RescheduleProposal.Status.PENDING);
    }

    @Test void checkedInPatientCannotBeMarkedAbsentAndActiveVisitCannotBeWithdrawn() {
        var f = fixture(now().minusMinutes(40)); var admin = account("ADMIN"); authenticate(admin); service.checkIn(f.a().getId());
        expect(ErrorCode.VISIT_ALREADY_RECEIVED, () -> booking.adminNoShow(f.a().getId(), new AppointmentReasonRequest("Vắng")));
        authenticate(f.doctor().getAccount()); expect(ErrorCode.VISIT_ALREADY_RECEIVED, () -> booking.updateStatus(f.a().getId(),new UpdateAppointmentStatusRequest(AppointmentStatus.NO_SHOW)));
        service.start(f.a().getId()); authenticate(admin);
        expect(ErrorCode.VISIT_NOT_WAITING, () -> service.cancelReception(f.a().getId(),new AppointmentReasonRequest("Rút")));
    }

    @Test void leaveBlocksReceptionAndApprovalCannotCancelReceivedPatient() {
        var f = fixture(now().plusMinutes(20)); var admin = account("ADMIN"); authenticate(admin); service.checkIn(f.a().getId());
        var leave = new DoctorLeave(); leave.setDoctor(f.doctor()); leave.setStartAt(now().plusMinutes(10));
        leave.setEndAt(now().plusHours(2)); leave.setReason("Nghỉ"); leaves.saveAndFlush(leave);
        expect(ErrorCode.SCHEDULE_NOT_AVAILABLE, () -> service.checkIn(f.a().getId()));
        expect(ErrorCode.VISIT_ALREADY_RECEIVED, () -> planning.review(leave.getId(),new PlanningDtos.ReviewRequest("APPROVED",true,"Nghỉ")));
        assertThat(appointments.findById(f.a().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test void concurrentStartsHaveOneWinnerAndDoctorBecomesAvailableAfterCompletion() throws Exception {
        var first = fixture(now().minusMinutes(10)); var second = fixtureFor(first.doctor(),now().minusMinutes(40));
        var admin = account("ADMIN"); authenticate(admin); service.checkIn(first.a().getId()); service.checkIn(second.a().getId());
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var r1 = pool.submit(() -> tryStart(first,start)); var r2 = pool.submit(() -> tryStart(second,start)); start.countDown();
            assertThat(List.of(r1.get(15,TimeUnit.SECONDS),r2.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); }
        var active = visits.findByActiveDoctorId(first.doctor().getId()).orElseThrow();
        Long activeAppointmentId = active.getAppointment().getId();
        authenticate(first.doctor().getAccount()); booking.complete(activeAppointmentId,new MedicalRecordRequest("Kết quả",null,null));
        Long other = activeAppointmentId.equals(first.a().getId()) ? second.a().getId() : first.a().getId();
        assertThat(service.start(other).status()).isEqualTo(ClinicVisit.Status.IN_PROGRESS);
    }

    @Test void latePatientUsesArrivalPriorityAndWaitingQueueIsPaged() {
        var first = fixture(now().minusMinutes(40)); var second = fixtureFor(first.doctor(),now().minusMinutes(10));
        authenticate(account("ADMIN")); var received = service.checkIn(first.a().getId()); service.checkIn(second.a().getId());
        assertThat(received.priorityAt()).isEqualTo(now());
        var board = service.board(first.doctor().getId(),now().toLocalDate(),0,1,false);
        assertThat(board.waiting().totalElements()).isEqualTo(2);
        assertThat(board.waiting().content().get(0).appointmentId()).isEqualTo(first.a().getId());
        assertThat(service.board(first.doctor().getId(),now().toLocalDate(),1,1,false).waiting().content().get(0).appointmentId()).isEqualTo(second.a().getId());
    }

    @Test void unfinishedVisitFromPreviousDayRemainsVisibleAndBlocksNewStart() {
        var old = fixture(now().minusDays(1)); var current = fixtureFor(old.doctor(),now().minusMinutes(10));
        var admin = account("ADMIN");
        var active = new ClinicVisit(); active.setAppointment(old.a()); active.setStatus(ClinicVisit.Status.IN_PROGRESS);
        active.setActiveDoctorId(old.doctor().getId()); active.setCheckedInBy(admin.getId());
        active.setCheckedInAt(now().minusDays(1)); active.setPriorityAt(now().minusDays(1)); active.setStartedAt(now().minusDays(1)); visits.saveAndFlush(active);
        authenticate(admin); service.checkIn(current.a().getId());
        var board = service.board(old.doctor().getId(),now().toLocalDate(),0,20,false);
        assertThat(board.activeVisit().appointmentId()).isEqualTo(old.a().getId());
        assertThat(board.waiting().totalElements()).isEqualTo(1);
        authenticate(old.doctor().getAccount()); expect(ErrorCode.DOCTOR_BUSY, () -> service.start(current.a().getId()));
        booking.complete(old.a().getId(),new MedicalRecordRequest("Hoàn thành lượt cũ",null,null));
        assertThat(service.start(current.a().getId()).status()).isEqualTo(ClinicVisit.Status.IN_PROGRESS);
    }

    @Test void failureToNotifyCompletionRollsBackVisitAndCompletionEvent() {
        var f = fixture(now().minusMinutes(10)); authenticate(account("ADMIN")); var v = service.checkIn(f.a().getId());
        authenticate(f.doctor().getAccount()); service.start(f.a().getId());
        NotificationService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notifications);
        doThrow(new IllegalStateException("test notification failure")).when(target).appointmentChanged(any(),eq(com.xxxx.clinicbookingsystem.notification.entity.NotificationType.APPOINTMENT_COMPLETED));
        assertThatThrownBy(() -> booking.complete(f.a().getId(),new MedicalRecordRequest("Kết quả",null,null))).isInstanceOf(IllegalStateException.class);
        assertThat(visits.findById(v.id()).orElseThrow().getStatus()).isEqualTo(ClinicVisit.Status.IN_PROGRESS);
        assertThat(events.findByVisitIdOrderByIdAsc(v.id())).hasSize(2);
    }

    @Test void endpointsEnforceReceptionRoleDoctorOwnershipAndPagination() throws Exception {
        var f = fixture(now().minusMinutes(10)); var admin = account("ADMIN"); var other = fixture(now());
        String checkIn = "/api/v1/admin/appointments/"+f.a().getId()+"/check-in";
        mvc.perform(post(checkIn).header("Authorization",token(f.a().getPatient()))).andExpect(status().isForbidden());
        mvc.perform(post(checkIn).header("Authorization",token(admin))).andExpect(status().isOk());
        String start = "/api/v1/doctors/me/appointments/"+f.a().getId()+"/start";
        mvc.perform(post(start).header("Authorization",token(other.doctor().getAccount()))).andExpect(status().isForbidden());
        mvc.perform(post(start).header("Authorization",token(f.doctor().getAccount()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/doctors/me/queue").header("Authorization",token(f.doctor().getAccount())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.activeVisit.appointmentId").value(f.a().getId()));
        mvc.perform(get("/api/v1/doctors/me/queue").header("Authorization",token(other.doctor().getAccount())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.activeVisit").isEmpty());
        mvc.perform(get("/api/v1/admin/queue").param("doctorId",f.doctor().getId().toString()).param("size","101").header("Authorization",token(admin)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/doctors/me/queue")).andExpect(status().isUnauthorized());
    }

    private boolean tryStart(Fixture f, CountDownLatch start) throws Exception {
        authenticate(f.doctor().getAccount());
        try { start.await(); service.start(f.a().getId()); return true; }
        catch (AppException e) { assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCTOR_BUSY); return false; }
        finally { clear(); }
    }
    private Fixture fixture(LocalDateTime time) {
        var specialty = new Specialty(); specialty.setName("Visit "+UUID.randomUUID()); specialties.saveAndFlush(specialty);
        var doctor = new Doctor(); doctor.setAccount(account("DOCTOR")); doctor.setSpecialty(specialty); doctor.setFullName("Visit Doctor"); doctors.saveAndFlush(doctor);
        return fixtureFor(doctor,time);
    }
    private Fixture fixtureFor(Doctor doctor, LocalDateTime time) {
        var a = new Appointment(); a.setDoctor(doctor); a.setPatient(account("PATIENT")); a.setStatus(AppointmentStatus.CONFIRMED);
        var slot = slot(doctor,time); slot.setStatus(SlotStatus.BOOKED); slots.saveAndFlush(slot); a.setSlot(slot); appointments.saveAndFlush(a);
        return new Fixture(a,doctor);
    }
    private ScheduleSlot slot(Doctor doctor, LocalDateTime time) {
        var schedule = new DoctorSchedule(); schedule.setDoctor(doctor); schedule.setStartAt(time); schedule.setEndAt(time.plusMinutes(30));
        var slot = new ScheduleSlot(); slot.setStartAt(time); slot.setEndAt(time.plusMinutes(30)); schedule.addSlot(slot); schedules.saveAndFlush(schedule); return slot;
    }
    private Account account(String role) { var a = new Account(); String name=UUID.randomUUID().toString(); a.setUsername(name); a.setEmail(name+"@test.com"); a.setPassword("unused"); a.setRole(roles.findByRoleName(role).orElseThrow()); return accounts.saveAndFlush(a); }
    private void authenticate(Account a) { var p = new CustomUserDetails(a); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities())); }
    private String token(Account a) { clear(); return "Bearer "+jwt.generateToken(a.getUsername()); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private void expect(ErrorCode code, Runnable action) { assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,e -> assertThat(e.getErrorCode()).isEqualTo(code)); }
    private record Fixture(Appointment a, Doctor doctor) {}
}
