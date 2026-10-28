package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.auth.jwt.JwtService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleRepository;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleSlotRepository;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AppointmentLifecycleIntegrationTests.Time.class)
class AppointmentLifecycleIntegrationTests {
    @TestConfiguration
    static class Time {
        @Bean @Primary Clock fixedClock() {
            return Clock.fixed(Instant.parse("2030-10-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired Clock clock;
    @Autowired AppointmentService service;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired DoctorRepository doctors;
    @Autowired SpecialtyRepository specialties;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleSlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.xxxx.clinicbookingsystem.appointment.visit.VisitService visits;

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void expiryOnlyClosesPendingAfterEndAndIsIdempotent() {
        var f = fixture(now().minusHours(1));
        var pending = appointment(f, AppointmentStatus.PENDING);
        var confirmed = appointment(fixture(now().minusHours(1)), AppointmentStatus.CONFIRMED);
        var future = appointment(fixture(now().plusHours(1)), AppointmentStatus.PENDING);
        assertThat(service.duePendingIds()).contains(pending.getId()).doesNotContain(confirmed.getId(), future.getId());
        service.expirePending(pending.getId());
        service.expirePending(pending.getId());
        service.expirePending(confirmed.getId());
        service.expirePending(future.getId());
        assertThat(appointments.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
        assertThat(appointments.findById(confirmed.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointments.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(slots.findById(f.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BLOCKED);
    }

    @Test
    void cannotCompleteFutureAppointmentOrConfirmAnAlreadyStartedOne() {
        var future = fixture(now().plusHours(1));
        var confirmed = appointment(future, AppointmentStatus.CONFIRMED);
        authenticate(future.doctorAccount());
        expectStatusError(() -> service.updateStatus(confirmed.getId(), new UpdateAppointmentStatusRequest(AppointmentStatus.COMPLETED)));
        var past = fixture(now().minusMinutes(10));
        var pending = appointment(past, AppointmentStatus.PENDING);
        authenticate(past.doctorAccount());
        expectStatusError(() -> service.updateStatus(pending.getId(), new UpdateAppointmentStatusRequest(AppointmentStatus.CONFIRMED)));
    }

    @Test
    void noShowRequiresEndOfSlotAndOwnerDoctor() {
        var current = fixture(now().minusMinutes(10));
        var appt = appointment(current, AppointmentStatus.CONFIRMED);
        authenticate(current.doctorAccount());
        expectStatusError(() -> service.updateStatus(appt.getId(), new UpdateAppointmentStatusRequest(AppointmentStatus.NO_SHOW)));
        authenticate(account("ADMIN")); visits.checkIn(appt.getId());
        authenticate(current.doctorAccount()); visits.start(appt.getId());
        assertThat(service.complete(appt.getId(), new com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordRequest("Kết quả kiểm thử", null, null)).status())
                .isEqualTo(AppointmentStatus.COMPLETED);

        var past = fixture(now().minusHours(1));
        var missed = appointment(past, AppointmentStatus.CONFIRMED);
        authenticate(current.doctorAccount());
        expectStatusError(() -> service.updateStatus(missed.getId(), new UpdateAppointmentStatusRequest(AppointmentStatus.NO_SHOW)));
        authenticate(past.doctorAccount());
        assertThat(service.updateStatus(missed.getId(), new UpdateAppointmentStatusRequest(AppointmentStatus.NO_SHOW)).status())
                .isEqualTo(AppointmentStatus.NO_SHOW);
        expectStatusError(() -> service.updateStatus(missed.getId(), new UpdateAppointmentStatusRequest(AppointmentStatus.COMPLETED)));
    }

    @Test
    void overlappingDoctorsAreRejectedButAdjacentAppointmentsAreAllowed() {
        var first = fixture(now().plusHours(2));
        var overlap = fixture(now().plusHours(2).plusMinutes(15));
        var adjacent = fixture(now().plusHours(2).plusMinutes(30));
        authenticate(first.patient());
        var booked = service.book(new CreateAppointmentRequest(first.slotId()));
        assertThatThrownBy(() -> service.book(new CreateAppointmentRequest(overlap.slotId())))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PATIENT_SCHEDULE_CONFLICT));
        assertThat(slots.findById(overlap.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        var adjacentBooking = service.book(new CreateAppointmentRequest(adjacent.slotId()));
        assertThat(adjacentBooking.status()).isEqualTo(AppointmentStatus.PENDING);
        service.cancel(booked.id());
        service.cancel(adjacentBooking.id());
        assertThat(service.book(new CreateAppointmentRequest(overlap.slotId())).status()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void samePatientConcurrentRequestsCannotBookOverlappingDoctors() throws Exception {
        var first = fixture(now().plusHours(3));
        var second = fixture(now().plusHours(3));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> tryBook(first.patient(), first.slotId(), ready, start));
            var two = executor.submit(() -> tryBook(first.patient(), second.slotId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat((one.get(10, TimeUnit.SECONDS) ? 1 : 0) + (two.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            authenticate(first.patient());
            assertThat(service.mine(0, 20).content()).hasSize(1);
            long bookedSlots = java.util.stream.Stream.of(first.slotId(), second.slotId())
                    .filter(id -> slots.findById(id).orElseThrow().getStatus() == SlotStatus.BOOKED).count();
            assertThat(bookedSlots).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void adminCanCancelForLockedDoctorButPatientCannotUseAdminEndpoint() throws Exception {
        var f = fixture(now().plusHours(1));
        var appt = appointment(f, AppointmentStatus.PENDING);
        var account = f.doctorAccount();
        account.setIsActive(false);
        accounts.saveAndFlush(account);
        String path = "/api/v1/admin/appointments/" + appt.getId() + "/cancel";
        mvc.perform(patch(path).header("Authorization", token(f.patient())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bác sĩ nghỉ\"}"))
                .andExpect(status().isForbidden());
        var admin = account("ADMIN");
        mvc.perform(patch(path).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Bác sĩ ngừng công tác\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.statusReason").value("Bác sĩ ngừng công tác"));
        assertThat(slots.findById(f.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        mvc.perform(patch(path).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hủy lần hai\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanRecordNoShowForLockedDoctorAfterEnd() throws Exception {
        var f = fixture(now().minusHours(1));
        var appt = appointment(f, AppointmentStatus.CONFIRMED);
        var account = f.doctorAccount();
        account.setIsActive(false); accounts.saveAndFlush(account);
        mvc.perform(patch("/api/v1/admin/appointments/" + appt.getId() + "/no-show")
                        .header("Authorization", token(account("ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Phòng khám xác nhận bệnh nhân không đến\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NO_SHOW"));
    }

    @Test
    void statusFilterCountsAllPagesAndDoesNotLeakOtherPatients() throws Exception {
        var f = fixture(now().plusHours(1));
        var one = appointment(f, AppointmentStatus.CANCELED);
        var f2 = fixture(now().plusHours(2));
        var two = appointment(f2, AppointmentStatus.CANCELED);
        two.setPatient(f.patient()); appointments.saveAndFlush(two);
        appointment(fixture(now().plusHours(3)), AppointmentStatus.CANCELED);
        mvc.perform(get("/api/v1/appointments/me").header("Authorization", token(f.patient()))
                        .param("status", "CANCELED").param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(two.getId()));
        mvc.perform(get("/api/v1/appointments/me").header("Authorization", token(f.patient()))
                        .param("status", "CANCELED").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(one.getId()));
    }

    @Test
    void databaseRejectsMisspelledStatus() {
        var f = fixture(now().plusHours(1));
        var appt = appointment(f, AppointmentStatus.PENDING);
        assertThatThrownBy(() -> jdbc.update("UPDATE appointments SET status = 'CANCEL' WHERE appointment_id = ?", appt.getId()))
                .isInstanceOfSatisfying(DataAccessException.class, error -> {
                    assertThat(error.getMostSpecificCause()).isInstanceOf(java.sql.SQLException.class);
                    int code = ((java.sql.SQLException) error.getMostSpecificCause()).getErrorCode();
                    assertThat(code).isIn(23513, 3819);
                });
    }

    @Test
    void doctorSummaryCountsBeyondOnePageAndExcludesOtherDoctorsAndClosedSchedules() throws Exception {
        var f = fixture(now().plusHours(1));
        for (int i = 0; i < 55; i++) appointment(f, AppointmentStatus.CANCELED);
        appointment(f, AppointmentStatus.PENDING);
        appointment(fixture(now().plusHours(2)), AppointmentStatus.CONFIRMED);
        for (int i = 0; i < 2; i++) {
            var schedule = new DoctorSchedule();
            schedule.setDoctor(f.doctor());
            schedule.setActive(i == 0);
            schedule.setStartAt(i == 0 ? now().minusHours(2) : now().plusHours(3));
            schedule.setEndAt(schedule.getStartAt().plusMinutes(30));
            schedules.saveAndFlush(schedule);
        }
        mvc.perform(get("/api/v1/doctors/me/appointments/summary").header("Authorization", token(f.doctorAccount())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAppointments").value(56))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.confirmedCount").value(0))
                .andExpect(jsonPath("$.data.completedCount").value(0))
                .andExpect(jsonPath("$.data.openScheduleCount").value(1));
        mvc.perform(get("/api/v1/doctors/me/appointments/summary").header("Authorization", token(f.patient())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/doctors/me/appointments/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctors/me/appointments").param("status", "CANCELED").param("size", "1")
                        .header("Authorization", token(f.doctorAccount())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(55));
    }

    private boolean tryBook(Account patient, Long slotId, CountDownLatch ready, CountDownLatch start) throws Exception {
        authenticate(patient); ready.countDown(); start.await(5, TimeUnit.SECONDS);
        try {
            service.book(new CreateAppointmentRequest(slotId)); return true;
        } catch (AppException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PATIENT_SCHEDULE_CONFLICT); return false;
        } finally { SecurityContextHolder.clearContext(); }
    }

    private Fixture fixture(LocalDateTime start) {
        var doctorAccount = account("DOCTOR");
        var patient = account("PATIENT");
        var specialty = new Specialty(); specialty.setName("Lifecycle " + UUID.randomUUID()); specialty = specialties.saveAndFlush(specialty);
        var doctor = new Doctor(); doctor.setAccount(doctorAccount); doctor.setSpecialty(specialty); doctor.setFullName("Lifecycle Doctor"); doctor = doctors.saveAndFlush(doctor);
        var schedule = new DoctorSchedule(); schedule.setDoctor(doctor); schedule.setStartAt(start); schedule.setEndAt(start.plusMinutes(30));
        var slot = new ScheduleSlot(); slot.setStartAt(start); slot.setEndAt(start.plusMinutes(30)); schedule.addSlot(slot); schedules.saveAndFlush(schedule);
        return new Fixture(doctorAccount, patient, doctor, slot.getId());
    }

    private Appointment appointment(Fixture f, AppointmentStatus status) {
        var appt = new Appointment(); appt.setPatient(f.patient()); appt.setDoctor(f.doctor()); appt.setSlot(slots.findById(f.slotId()).orElseThrow()); appt.setStatus(status);
        var slot = slots.findById(f.slotId()).orElseThrow(); slot.setStatus(status == AppointmentStatus.CANCELED ? SlotStatus.AVAILABLE : SlotStatus.BOOKED); slots.saveAndFlush(slot);
        return appointments.saveAndFlush(appt);
    }
    private Account account(String role) {
        var account = new Account(); String name = UUID.randomUUID().toString(); account.setUsername(name); account.setEmail(name + "@test.com"); account.setPassword("unused"); account.setRole(roles.findByRoleName(role).orElseThrow()); return accounts.saveAndFlush(account);
    }
    private void authenticate(Account account) {
        var principal = new CustomUserDetails(account);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private String token(Account account) { return "Bearer " + jwt.generateToken(account.getUsername()); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private void expectStatusError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_APPOINTMENT_STATUS));
    }
    private record Fixture(Account doctorAccount, Account patient, Doctor doctor, Long slotId) {}
}
