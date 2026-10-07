package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.*;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.entity.Role;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.dto.*;
import com.xxxx.clinicbookingsystem.schedule.service.ScheduleService;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BookingFlowIntegrationTests {
    @Autowired ScheduleService scheduleService;
    @Autowired AppointmentService appointmentService;
    @Autowired AccountRepository accountRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired SpecialtyRepository specialtyRepository;
    @Autowired DoctorRepository doctorRepository;

    @Test
    void completeBookingFlowAndPreventDuplicateSlot() {
        Role doctorRole = roleRepository.findByRoleName("DOCTOR").orElseThrow();
        Role patientRole = roleRepository.findByRoleName("PATIENT").orElseThrow();
        Account doctorAccount = account("doctor_flow", doctorRole);
        Account patientOne = account("patient_one", patientRole);
        Account patientTwo = account("patient_two", patientRole);

        Specialty specialty = new Specialty();
        specialty.setName("Cardiology");
        specialty = specialtyRepository.save(specialty);
        Doctor doctor = new Doctor();
        doctor.setAccount(doctorAccount);
        doctor.setSpecialty(specialty);
        doctor.setFullName("Doctor Flow");
        doctor = doctorRepository.save(doctor);

        authenticate(doctorAccount);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        ScheduleResponse schedule = scheduleService.create(new CreateScheduleRequest(start, start.plusHours(1)));
        assertThat(schedule.slots()).hasSize(2);
        assertThatThrownBy(() -> scheduleService.create(new CreateScheduleRequest(start.plusMinutes(30), start.plusHours(2))))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SCHEDULE_OVERLAPPED));

        Long slotId = schedule.slots().get(0).id();
        authenticate(patientOne);
        AppointmentResponse appointment = appointmentService.book(new CreateAppointmentRequest(slotId));
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.PENDING);

        authenticate(patientTwo);
        assertThatThrownBy(() -> appointmentService.book(new CreateAppointmentRequest(slotId)))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SLOT_ALREADY_BOOKED));

        authenticate(doctorAccount);
        AppointmentResponse confirmed = appointmentService.updateStatus(appointment.id(),
                new UpdateAppointmentStatusRequest(AppointmentStatus.CONFIRMED));
        assertThat(confirmed.status()).isEqualTo(AppointmentStatus.CONFIRMED);

        authenticate(patientOne);
        assertThat(appointmentService.mine(0, 20).content()).extracting(AppointmentResponse::status)
                .containsExactly(AppointmentStatus.CONFIRMED);
        appointmentService.cancel(appointment.id());
        assertThat(scheduleService.availableSlots(doctor.getId(), start.toLocalDate()))
                .extracting(SlotResponse::id).contains(slotId);
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentBookingAllowsExactlyOnePatient() throws Exception {
        Role doctorRole = roleRepository.findByRoleName("DOCTOR").orElseThrow();
        Role patientRole = roleRepository.findByRoleName("PATIENT").orElseThrow();
        Account doctorAccount = account("doctor_concurrent", doctorRole);
        Account first = account("concurrent_one", patientRole);
        Account second = account("concurrent_two", patientRole);
        Specialty specialty = new Specialty();
        specialty.setName("Neurology");
        specialty = specialtyRepository.save(specialty);
        Doctor doctor = new Doctor();
        doctor.setAccount(doctorAccount);
        doctor.setSpecialty(specialty);
        doctor.setFullName("Doctor Concurrent");
        doctorRepository.save(doctor);

        authenticate(doctorAccount);
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Long slotId = scheduleService.create(new CreateScheduleRequest(start, start.plusMinutes(30))).slots().get(0).id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> tryBook(first, slotId, ready, startTogether));
            Future<Boolean> secondResult = executor.submit(() -> tryBook(second, slotId, ready, startTogether));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            int successCount = (firstResult.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (secondResult.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean tryBook(Account account, Long slotId, CountDownLatch ready, CountDownLatch startTogether) throws InterruptedException {
        authenticate(account);
        ready.countDown();
        startTogether.await();
        try {
            appointmentService.book(new CreateAppointmentRequest(slotId));
            return true;
        } catch (AppException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SLOT_ALREADY_BOOKED);
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Account account(String username, Role role) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("encoded-password");
        account.setEmail(username + "@example.com");
        account.setRole(role);
        account.setIsActive(true);
        return accountRepository.save(account);
    }

    private void authenticate(Account account) {
        CustomUserDetails principal = new CustomUserDetails(account);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
