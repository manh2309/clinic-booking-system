package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.CreateAppointmentRequest;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.dto.CreateScheduleRequest;
import com.xxxx.clinicbookingsystem.schedule.dto.ScheduleResponse;
import com.xxxx.clinicbookingsystem.schedule.dto.SlotResponse;
import com.xxxx.clinicbookingsystem.schedule.entity.SlotStatus;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleRepository;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleSlotRepository;
import com.xxxx.clinicbookingsystem.schedule.service.ScheduleService;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScheduleLifecycleIntegrationTests {
    @Autowired ScheduleService service;
    @Autowired AppointmentService booking;
    @Autowired AccountRepository accounts;
    @Autowired DoctorRepository doctors;
    @Autowired RoleRepository roles;
    @Autowired SpecialtyRepository specialties;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleSlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void restoreReusesSlotsAndKeepsCanceledAppointmentHistory() {
        var f = fixture();
        var schedule = create(f, tomorrow());
        authenticate(f.patient());
        var appointment = booking.book(new CreateAppointmentRequest(schedule.slots().get(0).id()));
        booking.cancel(appointment.id());
        authenticate(f.account());
        service.deactivate(schedule.id());
        service.deactivate(schedule.id());
        assertThat(service.mySchedules()).filteredOn(s -> s.id().equals(schedule.id()))
                .extracting(ScheduleResponse::active).containsExactly(false);
        assertThat(service.availableSlots(f.doctor().getId(), tomorrow().toLocalDate())).isEmpty();
        var restored = service.restore(schedule.id());
        assertThat(restored.active()).isTrue();
        assertThat(restored.slots()).extracting(SlotResponse::id).containsExactlyElementsOf(schedule.slots().stream().map(SlotResponse::id).toList());
        assertThat(restored.slots()).extracting(SlotResponse::status).containsOnly(SlotStatus.AVAILABLE);
        assertThat(appointments.findById(appointment.id()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELED);
        assertThat(service.restore(schedule.id()).id()).isEqualTo(schedule.id());
    }

    @Test
    void creatingOverCanceledIntervalReturnsRestoreInstruction() {
        var f = fixture();
        var schedule = create(f, tomorrow());
        service.deactivate(schedule.id());
        expectError(() -> service.create(new CreateScheduleRequest(tomorrow(), tomorrow().plusHours(1))), ErrorCode.SCHEDULE_RESTORE_REQUIRED);
    }

    @Test
    void scheduleOwnerCannotCancelWhenSlotIsBooked() {
        var f = fixture();
        var schedule = create(f, tomorrow());
        authenticate(f.patient());
        booking.book(new CreateAppointmentRequest(schedule.slots().get(0).id()));
        authenticate(f.account());
        expectError(() -> service.deactivate(schedule.id()), ErrorCode.SCHEDULE_NOT_AVAILABLE);
        assertThat(schedules.findById(schedule.id()).orElseThrow().getActive()).isTrue();
    }

    @Test
    void anotherDoctorCannotCancelOrRestoreOwnedSchedule() {
        var f = fixture();
        var schedule = create(f, tomorrow());
        var other = fixture();
        authenticate(other.account());
        expectError(() -> service.deactivate(schedule.id()), ErrorCode.SCHEDULE_NOT_AVAILABLE);
        expectError(() -> service.restore(schedule.id()), ErrorCode.SCHEDULE_NOT_AVAILABLE);
    }

    @Test
    void pastSchedulesCannotBeRestoredOrCanceled() {
        var f = fixture();
        var schedule = create(f, tomorrow());
        service.deactivate(schedule.id());
        var stored = schedules.findById(schedule.id()).orElseThrow();
        stored.setStartAt(LocalDateTime.now(clock).minusHours(1));
        stored.setEndAt(LocalDateTime.now(clock));
        schedules.saveAndFlush(stored);
        expectError(() -> service.restore(schedule.id()), ErrorCode.SCHEDULE_NOT_AVAILABLE);
        expectError(() -> service.deactivate(schedule.id()), ErrorCode.SCHEDULE_NOT_AVAILABLE);
    }

    @Test
    void dayFilterExcludesMidnightOfNextDayAndRejectsFractionalSeconds() {
        var f = fixture();
        var first = create(f, tomorrow());
        create(f, tomorrow().plusDays(1).toLocalDate().atStartOfDay());
        assertThat(service.availableSlots(f.doctor().getId(), tomorrow().toLocalDate()))
                .extracting(SlotResponse::id).containsExactlyElementsOf(first.slots().stream().map(SlotResponse::id).toList());
        var fractional = tomorrow().plusDays(2).plusNanos(1);
        expectError(() -> service.create(new CreateScheduleRequest(fractional, fractional.plusHours(1))), ErrorCode.INVALID_SCHEDULE_TIME);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bookingAndDeactivationAreSerializedInBothOrders(boolean deactivateFirst) throws Exception {
        var f = fixture();
        var schedule = create(f, tomorrow());
        Long slotId = schedule.slots().get(0).id();
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                authenticate(deactivateFirst ? f.account() : f.patient());
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        if (deactivateFirst) service.deactivate(schedule.id());
                        else booking.book(new CreateAppointmentRequest(slotId));
                        holding.countDown();
                        await(release);
                    });
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            Future<ErrorCode> second = executor.submit(() -> {
                authenticate(deactivateFirst ? f.patient() : f.account());
                attempting.countDown();
                try {
                    if (deactivateFirst) booking.book(new CreateAppointmentRequest(slotId));
                    else service.deactivate(schedule.id());
                    return null;
                } catch (AppException e) { return e.getErrorCode(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(deactivateFirst ? ErrorCode.SLOT_ALREADY_BOOKED : ErrorCode.SCHEDULE_NOT_AVAILABLE);
            assertThat(schedules.findById(schedule.id()).orElseThrow().getActive()).isEqualTo(!deactivateFirst);
            assertThat(slots.findById(slotId).orElseThrow().getStatus()).isEqualTo(deactivateFirst ? SlotStatus.BLOCKED : SlotStatus.BOOKED);
            assertThat(appointments.findFiltered(f.patient().getId(), f.doctor().getId(), null, PageRequest.of(0, 20)).getTotalElements())
                    .isEqualTo(deactivateFirst ? 0 : 1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private ScheduleResponse create(Fixture f, LocalDateTime start) {
        authenticate(f.account());
        return service.create(new CreateScheduleRequest(start, start.plusHours(1)));
    }

    private Fixture fixture() {
        var doctorAccount = account("DOCTOR");
        var patient = account("PATIENT");
        var specialty = new Specialty();
        specialty.setName("Schedule " + UUID.randomUUID());
        specialty = specialties.saveAndFlush(specialty);
        var doctor = new Doctor();
        doctor.setAccount(doctorAccount);
        doctor.setSpecialty(specialty);
        doctor.setFullName("Schedule Doctor");
        return new Fixture(doctorAccount, patient, doctors.saveAndFlush(doctor));
    }

    private Account account(String role) {
        var account = new Account();
        var name = UUID.randomUUID().toString();
        account.setUsername(name);
        account.setEmail(name + "@test.com");
        account.setPassword("unused");
        account.setRole(roles.findByRoleName(role).orElseThrow());
        return accounts.saveAndFlush(account);
    }

    private void authenticate(Account account) {
        var principal = new CustomUserDetails(account);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private LocalDateTime tomorrow() { return LocalDateTime.now(clock).plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0); }
    private void expectError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock holder timed out");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private record Fixture(Account account, Account patient, Doctor doctor) {}
}
