package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.dto.CreateScheduleRequest;
import com.xxxx.clinicbookingsystem.schedule.entity.SlotStatus;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleSlotRepository;
import com.xxxx.clinicbookingsystem.schedule.service.ScheduleService;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DoctorBookingConcurrencyIntegrationTests {
    @Autowired AccountRepository accountRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired SpecialtyRepository specialtyRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired ScheduleSlotRepository slotRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ScheduleService scheduleService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void fixtureIsCommittedAndVisibleInANewTransaction() {
        Fixture fixture = createFixture();

        inTransaction(() -> {
            Doctor doctor = doctorRepository.findById(fixture.doctorId()).orElseThrow();
            var slot = slotRepository.findById(fixture.slotId()).orElseThrow();
            assertThat(doctor.getActive()).isTrue();
            assertThat(doctor.getAccount().getIsActive()).isTrue();
            assertThat(doctor.getSpecialty().getActive()).isTrue();
            assertThat(slot.getDoctor().getId()).isEqualTo(fixture.doctorId());
            assertThat(slot.getSchedule().getActive()).isTrue();
            assertThat(slot.getStartAt()).isAfter(LocalDateTime.now());
            assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
            assertThat(appointmentRepository.findAll())
                    .noneMatch(appointment -> appointment.getSlot().getId().equals(fixture.slotId()));
            return null;
        });

        assertThat(fixture.patient().getAuthorities()).extracting("authority").containsExactly("ROLE_PATIENT");
        assertThat(fixture.admin().getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private Fixture createFixture() {
        return Objects.requireNonNull(inTransaction(() -> {
            String suffix = UUID.randomUUID().toString();
            Account doctorAccount = account("doctor_" + suffix, "DOCTOR");
            Account patient = account("patient_" + suffix, "PATIENT");
            Account admin = account("admin_" + suffix, "ADMIN");
            Specialty specialty = new Specialty();
            specialty.setName("Concurrency " + suffix);
            specialty.setActive(true);
            specialtyRepository.save(specialty);
            Doctor doctor = new Doctor();
            doctor.setAccount(doctorAccount);
            doctor.setSpecialty(specialty);
            doctor.setFullName("Doctor Concurrency");
            doctor.setActive(true);
            doctorRepository.save(doctor);

            try {
                authenticate(new CustomUserDetails(doctorAccount));
                LocalDateTime start = LocalDateTime.now().plusDays(1)
                        .withHour(9).withMinute(0).withSecond(0).withNano(0);
                Long slotId = scheduleService.create(new CreateScheduleRequest(start, start.plusMinutes(30)))
                        .slots().get(0).id();
                return new Fixture(doctor.getId(), slotId, new CustomUserDetails(patient), new CustomUserDetails(admin));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }));
    }

    private Account account(String username, String roleName) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("encoded-password");
        account.setEmail(username + "@example.com");
        account.setRole(roleRepository.findByRoleName(roleName).orElseThrow());
        account.setIsActive(true);
        return accountRepository.save(account);
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(10);
        return transaction.execute(status -> action.get());
    }

    private <T> T asUserInTransaction(CustomUserDetails user, Supplier<T> action) {
        try {
            authenticate(user);
            return inTransaction(action);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(CustomUserDetails user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private record Fixture(Long doctorId, Long slotId, CustomUserDetails patient, CustomUserDetails admin) {}
}
