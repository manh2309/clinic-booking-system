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
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.medical.dto.MedicalRecordRequest;
import com.xxxx.clinicbookingsystem.medical.repository.MedicalRecordRepository;
import com.xxxx.clinicbookingsystem.notification.entity.NotificationType;
import com.xxxx.clinicbookingsystem.notification.repository.NotificationRepository;
import com.xxxx.clinicbookingsystem.notification.service.NotificationService;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.entity.*;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
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
import org.springframework.data.domain.PageRequest;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(AppointmentLifecycleIntegrationTests.Time.class)
class MedicalNotificationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired DoctorRepository doctors;
    @Autowired SpecialtyRepository specialties;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleSlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired MedicalRecordRepository records;
    @Autowired NotificationRepository notifications;
    @Autowired AppointmentService service;
    @Autowired JwtService jwt;
    @MockitoSpyBean NotificationService notificationService;
    private static final String RESULT = """
            {"diagnosis":"  Kết quả kiểm thử  ","examinationNotes":" Ghi nhận ","treatmentAdvice":" Hướng dẫn "}
            """;

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void markAllReadIsOwnedCoversAllPagesAndKeepsExistingReadTimes() throws Exception {
        var f = fixture(now().plusHours(1));
        authenticate(f.patient()); var a = service.book(new CreateAppointmentRequest(f.slotId()));
        authenticate(f.doctorAccount()); service.updateStatus(a.id(), new UpdateAppointmentStatusRequest(AppointmentStatus.CONFIRMED));
        authenticate(f.patient()); service.cancel(a.id());
        var first = notifications.findByRecipientId(f.patient().getId(), PageRequest.of(0, 1)).getContent().get(0);
        mvc.perform(patch("/api/v1/notifications/me/" + first.getId() + "/read").header("Authorization", token(f.patient())))
                .andExpect(status().isOk());
        var readAt = notifications.findById(first.getId()).orElseThrow().getReadAt();
        mvc.perform(patch("/api/v1/notifications/me/read-all").header("Authorization", token(f.patient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(2));
        assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isZero();
        assertThat(notifications.countByRecipientIdAndReadFalse(f.doctorAccount().getId())).isEqualTo(3);
        assertThat(notifications.findById(first.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);
        mvc.perform(patch("/api/v1/notifications/me/read-all").header("Authorization", token(f.patient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(0));
        mvc.perform(patch("/api/v1/notifications/me/read-all").header("Authorization", token(f.doctorAccount())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(3));
        mvc.perform(patch("/api/v1/notifications/me/read-all").header("Authorization", token(account("ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/notifications/me/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void completingSavesResultAndTwoPrivateNotificationsExactlyOnce() throws Exception {
        var f = fixture(now().minusMinutes(10));
        var a = appointment(f, AppointmentStatus.CONFIRMED);
        mvc.perform(post(completePath(a)).header("Authorization", token(f.doctorAccount()))
                        .contentType(MediaType.APPLICATION_JSON).content(RESULT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(records.existsByAppointmentId(a.getId())).isTrue();
        assertThat(slots.findById(f.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BLOCKED);
        mvc.perform(get("/api/v1/patient/me/medical-records/" + a.getId()).header("Authorization", token(f.patient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diagnosis").value("Kết quả kiểm thử"))
                .andExpect(jsonPath("$.data.examinationNotes").value("Ghi nhận"));
        mvc.perform(get("/api/v1/patient/me/medical-records").header("Authorization", token(f.patient())).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/doctors/me/appointments/" + a.getId() + "/medical-record").header("Authorization", token(f.doctorAccount())))
                .andExpect(status().isOk());
        mvc.perform(post(completePath(a)).header("Authorization", token(f.doctorAccount()))
                        .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isBadRequest());
        assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(1);
        assertThat(notifications.countByRecipientIdAndReadFalse(f.doctorAccount().getId())).isEqualTo(1);
        var notice = notifications.findByRecipientId(f.patient().getId(), PageRequest.of(0, 10)).getContent().get(0);
        assertThat(notice.getType()).isEqualTo(NotificationType.APPOINTMENT_COMPLETED);
        assertThat(notice.getMessage()).doesNotContain("Kết quả kiểm thử", "Ghi nhận", "Hướng dẫn");
    }

    @Test
    void cannotBypassResultValidationThroughOldStatusRoute() throws Exception {
        var f = fixture(now().minusMinutes(10));
        var a = appointment(f, AppointmentStatus.CONFIRMED);
        mvc.perform(patch("/api/v1/doctors/me/appointments/" + a.getId() + "/status").header("Authorization", token(f.doctorAccount()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5002));
        for (String body : new String[]{"{\"diagnosis\":\"   \"}", "{\"diagnosis\":\"" + "x".repeat(5001) + "\"}"}) {
            mvc.perform(post(completePath(a)).header("Authorization", token(f.doctorAccount()))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(appointments.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(records.existsByAppointmentId(a.getId())).isFalse();
        assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isZero();
    }

    @Test
    void resultOwnershipIsCheckedForBothRoles() throws Exception {
        var f = fixture(now().minusMinutes(10));
        var a = appointment(f, AppointmentStatus.CONFIRMED);
        var other = fixture(now().minusMinutes(10));
        mvc.perform(post(completePath(a)).header("Authorization", token(other.doctorAccount()))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isBadRequest());
        mvc.perform(post(completePath(a)).header("Authorization", token(f.patient()))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isForbidden());
        authenticate(f.doctorAccount()); service.complete(a.getId(), request());
        mvc.perform(get("/api/v1/patient/me/medical-records/" + a.getId()).header("Authorization", token(other.patient())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/patient/me/medical-records").header("Authorization", token(other.patient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/doctors/me/appointments/" + a.getId() + "/medical-record").header("Authorization", token(other.doctorAccount())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/patient/me/medical-records").header("Authorization", token(account("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void futurePendingAndInactiveDoctorsCannotComplete() throws Exception {
        var future = fixture(now().plusHours(1));
        var futureAppointment = appointment(future, AppointmentStatus.CONFIRMED);
        mvc.perform(post(completePath(futureAppointment)).header("Authorization", token(future.doctorAccount()))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isBadRequest());
        var f = fixture(now().minusMinutes(10));
        var a = appointment(f, AppointmentStatus.PENDING);
        mvc.perform(post(completePath(a)).header("Authorization", token(f.doctorAccount()))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isBadRequest());
        a.setStatus(AppointmentStatus.CONFIRMED); appointments.saveAndFlush(a);
        var doctor = f.doctor(); doctor.setActive(false); doctors.saveAndFlush(doctor);
        mvc.perform(post(completePath(a)).header("Authorization", token(f.doctorAccount()))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isConflict());
        assertThat(records.existsByAppointmentId(a.getId())).isFalse();
    }

    @Test
    void failureToSaveNotificationRollsBackResultAndStatus() {
        var f = fixture(now().minusMinutes(10));
        var a = appointment(f, AppointmentStatus.CONFIRMED);
        NotificationService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notificationService);
        doThrow(new IllegalStateException("notification failure")).when(target)
                .appointmentChanged(any(), eq(NotificationType.APPOINTMENT_COMPLETED));
        authenticate(f.doctorAccount());
        assertThatThrownBy(() -> service.complete(a.getId(), request())).isInstanceOf(IllegalStateException.class);
        assertThat(records.existsByAppointmentId(a.getId())).isFalse();
        assertThat(appointments.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(slots.findById(f.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void bookingConfirmationAndCancellationNotifyOnlyBothParticipants() throws Exception {
        var f = fixture(now().plusHours(1));
        authenticate(f.patient()); var a = service.book(new CreateAppointmentRequest(f.slotId()));
        authenticate(f.doctorAccount()); service.updateStatus(a.id(), new UpdateAppointmentStatusRequest(AppointmentStatus.CONFIRMED));
        authenticate(f.patient()); service.cancel(a.id());
        assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(3);
        assertThat(notifications.countByRecipientIdAndReadFalse(f.doctorAccount().getId())).isEqualTo(3);
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", token(f.patient())).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].type").value("APPOINTMENT_CANCELED"));
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", token(account("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readIsOwnedAndIdempotentAndExpiryDoesNotSendTwice() throws Exception {
        var f = fixture(now().minusHours(1)); var a = appointment(f, AppointmentStatus.PENDING);
        service.expirePending(a.getId()); service.expirePending(a.getId());
        assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(1);
        var n = notifications.findByRecipientId(f.patient().getId(), PageRequest.of(0, 10)).getContent().get(0);
        mvc.perform(patch("/api/v1/notifications/me/" + n.getId() + "/read").header("Authorization", token(f.doctorAccount())))
                .andExpect(status().isNotFound());
        String path = "/api/v1/notifications/me/" + n.getId() + "/read";
        mvc.perform(patch(path).header("Authorization", token(f.patient()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        var readAt = notifications.findById(n.getId()).orElseThrow().getReadAt();
        mvc.perform(patch(path).header("Authorization", token(f.patient()))).andExpect(status().isOk());
        assertThat(notifications.findById(n.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", token(f.patient())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void concurrentCompletionStoresOneResultAndOneNoticePerRecipient() throws Exception {
        var f = fixture(now().minusMinutes(10)); var a = appointment(f, AppointmentStatus.CONFIRMED);
        var start = new CountDownLatch(1); var ready = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> action = () -> {
            authenticate(f.doctorAccount()); ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                service.complete(a.getId(), request()); return true;
            } catch (com.xxxx.clinicbookingsystem.common.exception.AppException e) {
                assertThat(e.getErrorCode()).isEqualTo(com.xxxx.clinicbookingsystem.common.exception.ErrorCode.INVALID_APPOINTMENT_STATUS);
                return false;
            } finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = pool.submit(action); var second = pool.submit(action);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { start.countDown(); pool.shutdownNow(); }
        assertThat(records.existsByAppointmentId(a.getId())).isTrue();
        assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(1);
    }

    @Test
    void invalidPaginationReturnsBadRequestInsteadOfServerError() throws Exception {
        var patient = account("PATIENT");
        mvc.perform(get("/api/v1/notifications/me").param("page", "-1").header("Authorization", token(patient)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(get("/api/v1/patient/me/medical-records").param("size", "101").header("Authorization", token(patient)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
    }

    private String completePath(Appointment a) { return "/api/v1/doctors/me/appointments/" + a.getId() + "/complete"; }
    private MedicalRecordRequest request() { return new MedicalRecordRequest("Kết quả kiểm thử", null, null); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private Fixture fixture(LocalDateTime start) {
        var doctorAccount = account("DOCTOR"); var patient = account("PATIENT");
        var specialty = new Specialty(); specialty.setName("Medical " + UUID.randomUUID()); specialty = specialties.saveAndFlush(specialty);
        var doctor = new Doctor(); doctor.setAccount(doctorAccount); doctor.setSpecialty(specialty); doctor.setFullName("Medical Doctor"); doctor = doctors.saveAndFlush(doctor);
        var schedule = new DoctorSchedule(); schedule.setDoctor(doctor); schedule.setStartAt(start); schedule.setEndAt(start.plusMinutes(30));
        var slot = new ScheduleSlot(); slot.setStartAt(start); slot.setEndAt(start.plusMinutes(30)); schedule.addSlot(slot); schedules.saveAndFlush(schedule);
        return new Fixture(doctorAccount, patient, doctor, slot.getId());
    }
    private Appointment appointment(Fixture f, AppointmentStatus status) {
        var a = new Appointment(); a.setPatient(f.patient()); a.setDoctor(f.doctor()); a.setStatus(status);
        var slot = slots.findById(f.slotId()).orElseThrow(); slot.setStatus(SlotStatus.BOOKED); slots.saveAndFlush(slot);
        a.setSlot(slot); return appointments.saveAndFlush(a);
    }
    private Account account(String role) {
        var a = new Account(); var name = UUID.randomUUID().toString(); a.setUsername(name); a.setEmail(name + "@test.com");
        a.setPassword("unused"); a.setRole(roles.findByRoleName(role).orElseThrow()); return accounts.saveAndFlush(a);
    }
    private String token(Account account) {
        SecurityContextHolder.clearContext();
        return "Bearer " + jwt.generateToken(account.getUsername());
    }
    private void authenticate(Account account) {
        var principal = new CustomUserDetails(account);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private record Fixture(Account doctorAccount, Account patient, Doctor doctor, Long slotId) {}
}

