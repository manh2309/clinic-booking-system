package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.appointment.reschedule.*;
import com.xxxx.clinicbookingsystem.auth.jwt.JwtService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
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
class RescheduleIntegrationTests {
    @Autowired Clock clock;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired DoctorRepository doctors;
    @Autowired SpecialtyRepository specialties;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleSlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentService booking;
    @Autowired RescheduleService service;
    @Autowired RescheduleRepository proposals;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired DoctorLeaveRepository leaves;
    @Autowired LeaveContactRepository contacts;
    @MockitoSpyBean NotificationService notices;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void acceptMovesAtomicallyAndSecondAcceptanceCannotCreateAnotherBooking() {
        var f = fixture(); var p = propose(f);
        authenticate(f.patient()); var accepted = service.respond(f.appointment().getId(), p.id(), "accept", true);
        assertThat(accepted.status()).isEqualTo(RescheduleProposal.Status.ACCEPTED);
        var replacement = appointments.findById(accepted.replacementAppointmentId()).orElseThrow();
        assertThat(replacement.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(appointments.findById(f.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELED);
        assertThat(slots.findById(f.oldSlot()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slots.findById(f.newSlot()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), p.id(), "accept", true)).isInstanceOf(RuntimeException.class);
    }

    @Test void declineAndWithdrawKeepOldAppointmentAndAllowNewProposal() {
        var f = fixture(); var p = propose(f);
        assertThat(slots.findById(f.newSlot()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        authenticate(f.patient()); service.respond(f.appointment().getId(), p.id(), "decline", true);
        var second = propose(f);
        service.respond(f.appointment().getId(), second.id(), "withdraw", false);
        assertThat(appointments.findById(f.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(service.history(f.appointment().getId(), false)).hasSize(2);
    }

    @Test void occupiedTargetOrPatientOverlapKeepsOldBookingAndProposal() {
        var f = fixture(); var p = propose(f);
        authenticate(account("PATIENT")); booking.book(new CreateAppointmentRequest(f.newSlot()));
        authenticate(f.patient());
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), p.id(), "accept", true)).isInstanceOf(RuntimeException.class);
        assertThat(appointments.findById(f.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(proposals.findById(p.id()).orElseThrow().getStatus()).isEqualTo(RescheduleProposal.Status.PENDING);
    }

    @Test void canceledLeaveAppointmentCanBeInvitedOnceWithoutChangingOldHistory() {
        var f = fixture();
        var leave = new DoctorLeave(); leave.setDoctor(f.appointment().getDoctor()); leave.setStartAt(now().plusDays(2));
        leave.setEndAt(now().plusDays(2).plusHours(1)); leave.setReason("Nghỉ"); leave.setStatus("APPROVED"); leaves.saveAndFlush(leave);
        var old = f.appointment(); old.setStatus(AppointmentStatus.CANCELED); old.setStatusReason("Bác sĩ nghỉ"); appointments.saveAndFlush(old);
        var contact = new LeaveContact(); contact.setLeave(leave); contact.setAppointment(old); contacts.saveAndFlush(contact);
        var p = propose(f); authenticate(f.patient());
        service.respond(old.getId(), p.id(), "accept", true);
        assertThat(appointments.findById(old.getId()).orElseThrow().getStatusReason()).isEqualTo("Bác sĩ nghỉ");
        assertThatThrownBy(() -> propose(f)).isInstanceOf(RuntimeException.class);
    }

    @Test void targetLeaveAddedAfterProposalBlocksAcceptance() {
        var f = fixture(); var p = propose(f);
        var leave = new DoctorLeave(); leave.setDoctor(f.appointment().getDoctor()); leave.setStartAt(now().plusDays(3));
        leave.setEndAt(now().plusDays(3).plusHours(1)); leave.setReason("Nghỉ mới"); leaves.saveAndFlush(leave);
        authenticate(f.patient());
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), p.id(), "accept", true)).isInstanceOf(RuntimeException.class);
        assertThat(appointments.findById(f.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test void overlapWithAnotherDoctorIsRejectedWithoutLosingOriginal() {
        var f = fixture(); var p = propose(f); var other = fixture();
        var overlap = new Appointment(); overlap.setPatient(f.patient()); overlap.setDoctor(other.appointment().getDoctor());
        overlap.setSlot(slots.findById(other.newSlot()).orElseThrow()); overlap.setStatus(AppointmentStatus.CONFIRMED); appointments.saveAndFlush(overlap);
        authenticate(f.patient());
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), p.id(), "accept", true))
            .isInstanceOfSatisfying(com.xxxx.clinicbookingsystem.common.exception.AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(com.xxxx.clinicbookingsystem.common.exception.ErrorCode.PATIENT_SCHEDULE_CONFLICT));
        assertThat(slots.findById(f.newSlot()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test void twoPatientsAcceptingSameTargetProduceOneWinner() throws Exception {
        var first = fixture(); var p1 = propose(first);
        var secondPatient = account("PATIENT");
        var oldId = slot(first.appointment().getDoctor(), now().plusDays(4));
        var old = new Appointment(); old.setPatient(secondPatient); old.setDoctor(first.appointment().getDoctor());
        old.setSlot(slots.findById(oldId).orElseThrow()); old.setStatus(AppointmentStatus.CONFIRMED); appointments.saveAndFlush(old);
        var second = new Fixture(secondPatient, old, oldId, first.newSlot()); var p2 = propose(second);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var result1 = pool.submit(() -> raceAccept(first, p1.id(), start));
            var result2 = pool.submit(() -> raceAccept(second, p2.id(), start)); start.countDown();
            assertThat(List.of(result1.get(15, TimeUnit.SECONDS), result2.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            var statuses = List.of(appointments.findById(first.appointment().getId()).orElseThrow().getStatus(), appointments.findById(old.getId()).orElseThrow().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(AppointmentStatus.CANCELED, AppointmentStatus.CONFIRMED);
        } finally { pool.shutdownNow(); }
    }

    private boolean raceAccept(Fixture f, Long id, CountDownLatch start) throws Exception {
        authenticate(f.patient());
        try { start.await(); service.respond(f.appointment().getId(), id, "accept", true); return true; }
        catch (com.xxxx.clinicbookingsystem.common.exception.AppException e) { return false; }
        finally { clear(); }
    }

    @Test void expiryAndCanceledOriginalCannotBeAccepted() {
        var f = fixture(); var p = propose(f);
        var entity = proposals.findById(p.id()).orElseThrow(); entity.setExpiresAt(now().minusSeconds(1)); proposals.saveAndFlush(entity);
        authenticate(f.patient());
        assertThat(service.history(f.appointment().getId(), true).get(0).status()).isEqualTo(RescheduleProposal.Status.EXPIRED);
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), p.id(), "accept", true)).isInstanceOf(RuntimeException.class);
        var second = propose(f);
        authenticate(f.patient()); booking.cancel(f.appointment().getId());
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), second.id(), "accept", true)).isInstanceOf(RuntimeException.class);
    }

    @Test void pendingIsUniqueAndWrongDoctorOrInvalidDeadlineCannotBeProposed() {
        var f = fixture(); propose(f);
        assertThatThrownBy(() -> propose(f)).isInstanceOf(RuntimeException.class);
        var another = fixture();
        assertThatThrownBy(() -> service.propose(f.appointment().getId(), new RescheduleDtos.Create(another.newSlot(), "Đổi", now().plusHours(1)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.propose(another.appointment().getId(), new RescheduleDtos.Create(another.newSlot(), "Đổi", now().plusDays(8)))).isInstanceOf(RuntimeException.class);
    }

    @Test void notificationFailureRollsBackBothAppointmentsAndSlots() {
        var f = fixture(); var p = propose(f);
        NotificationService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notices);
        doThrow(new IllegalStateException("test failure")).when(target).appointmentChanged(any(), eq(com.xxxx.clinicbookingsystem.notification.entity.NotificationType.RESCHEDULE_ACCEPTED));
        authenticate(f.patient());
        assertThatThrownBy(() -> service.respond(f.appointment().getId(), p.id(), "accept", true)).isInstanceOf(IllegalStateException.class);
        assertThat(appointments.findById(f.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(slots.findById(f.newSlot()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(proposals.findById(p.id()).orElseThrow().getReplacement()).isNull();
    }

    @Test void endpointsEnforceRoleAndOwnership() throws Exception {
        var f = fixture(); var p = propose(f); var other = account("PATIENT");
        String url = "/api/v1/appointments/" + f.appointment().getId() + "/reschedules";
        mvc.perform(get(url).header("Authorization", token(other))).andExpect(status().isNotFound());
        mvc.perform(patch(url + "/" + p.id() + "/accept").header("Authorization", token(other))).andExpect(status().isNotFound());
        mvc.perform(get(url).header("Authorization", token(f.patient()))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mvc.perform(post("/api/v1/admin/appointments/" + f.appointment().getId() + "/reschedules").header("Authorization", token(f.patient()))
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test void httpCreateAcceptAndDoctorConfirmationRoundTrip() throws Exception {
        var f = fixture(); var admin = account("ADMIN");
        String adminUrl = "/api/v1/admin/appointments/" + f.appointment().getId() + "/reschedules";
        var body = json.writeValueAsString(new RescheduleDtos.Create(f.newSlot(), "x".repeat(500), now().plusHours(1)));
        var created = mvc.perform(post(adminUrl).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.proposedBy").value(admin.getId())).andReturn();
        long proposalId = json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        String acceptUrl = "/api/v1/appointments/" + f.appointment().getId() + "/reschedules/" + proposalId + "/accept";
        var accepted = mvc.perform(patch(acceptUrl).header("Authorization", token(f.patient())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED")).andReturn();
        long replacementId = json.readTree(accepted.getResponse().getContentAsString()).path("data").path("replacementAppointmentId").asLong();
        mvc.perform(get("/api/v1/appointments/me").header("Authorization", token(f.patient())).param("status", "PENDING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(replacementId));
        var doctorAccount = accounts.findById(f.appointment().getDoctor().getAccount().getId()).orElseThrow();
        mvc.perform(patch("/api/v1/doctors/me/appointments/" + replacementId + "/status")
            .header("Authorization", token(doctorAccount)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch(acceptUrl).header("Authorization", token(f.patient()))).andExpect(status().isConflict());
    }

    @Test void concurrentAcceptanceCreatesOneReplacement() throws Exception {
        var f = fixture(); var p = propose(f); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        Callable<Boolean> action = () -> { authenticate(f.patient()); try { start.await(); service.respond(f.appointment().getId(), p.id(), "accept", true); return true; }
            catch (com.xxxx.clinicbookingsystem.common.exception.AppException e) { return false; } finally { clear(); } };
        try { var first = pool.submit(action); var second = pool.submit(action); start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); }
    }

    private RescheduleDtos.Response propose(Fixture f) {
        authenticate(account("ADMIN"));
        return service.propose(f.appointment().getId(), new RescheduleDtos.Create(f.newSlot(), "Bố trí giờ khám khác", now().plusHours(1)));
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private Fixture fixture() {
        var patient = account("PATIENT"); var doctorAccount = account("DOCTOR");
        var specialty = new Specialty(); specialty.setName("Reschedule " + UUID.randomUUID()); specialties.saveAndFlush(specialty);
        var doctor = new Doctor(); doctor.setAccount(doctorAccount); doctor.setSpecialty(specialty); doctor.setFullName("Reschedule Doctor"); doctors.saveAndFlush(doctor);
        Long old = slot(doctor, now().plusDays(2)), target = slot(doctor, now().plusDays(3));
        var a = new Appointment(); a.setPatient(patient); a.setDoctor(doctor); a.setSlot(slots.findById(old).orElseThrow()); a.setStatus(AppointmentStatus.CONFIRMED);
        var oldEntity = slots.findById(old).orElseThrow(); oldEntity.setStatus(SlotStatus.BOOKED); slots.saveAndFlush(oldEntity); appointments.saveAndFlush(a);
        return new Fixture(patient, a, old, target);
    }
    private Long slot(Doctor doctor, LocalDateTime start) {
        var schedule = new DoctorSchedule(); schedule.setDoctor(doctor); schedule.setStartAt(start); schedule.setEndAt(start.plusMinutes(30));
        var slot = new ScheduleSlot(); slot.setStartAt(start); slot.setEndAt(start.plusMinutes(30)); schedule.addSlot(slot); schedules.saveAndFlush(schedule); return slot.getId();
    }
    private Account account(String role) {
        var a = new Account(); var name = UUID.randomUUID().toString(); a.setUsername(name); a.setEmail(name + "@test.com"); a.setPassword("unused");
        a.setRole(roles.findByRoleName(role).orElseThrow()); return accounts.saveAndFlush(a);
    }
    private String token(Account a) { clear(); return "Bearer " + jwt.generateToken(a.getUsername()); }
    private void authenticate(Account a) { var principal = new CustomUserDetails(a); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())); }
    private record Fixture(Account patient, Appointment appointment, Long oldSlot, Long newSlot) {}
}
