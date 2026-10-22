package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.auth.jwt.JwtService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.patient.dto.PatientProfileRequest;
import com.xxxx.clinicbookingsystem.patient.repository.PatientProfileRepository;
import com.xxxx.clinicbookingsystem.patient.service.PatientProfileService;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientProfileRecoveryIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PatientProfileRepository profiles;
    @Autowired JwtService jwt;
    @Autowired PatientProfileService service;
    private static final String PATH = "/api/v1/patient/me/profile";

    @Test
    void oldPatientCanReadAccountContactThenCreateAndUpdateOneProfile() throws Exception {
        var patient = account("PATIENT");
        mvc.perform(get(PATH).header("Authorization", token(patient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountId").value(patient.getId()))
                .andExpect(jsonPath("$.data.patientProfileId").isEmpty())
                .andExpect(jsonPath("$.data.fullName").isEmpty())
                .andExpect(jsonPath("$.data.phone").value("0962964504"))
                .andExpect(jsonPath("$.data.email").value(patient.getEmail()));
        assertThat(profiles.findByAccount_Id(patient.getId())).isEmpty();
        save(patient, "Nguyen Van A", patient.getEmail(), 200);
        Long id = profiles.findByAccount_Id(patient.getId()).orElseThrow().getPatientProfileId();
        save(patient, "Nguyen Van B", patient.getEmail(), 200);
        var saved = profiles.findByAccount_Id(patient.getId()).orElseThrow();
        assertThat(saved.getPatientProfileId()).isEqualTo(id);
        assertThat(saved.getFullName()).isEqualTo("Nguyen Van B");
        mvc.perform(get(PATH).header("Authorization", token(patient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Nguyen Van B"));
    }

    @Test
    void duplicateEmailAndInvalidNameDoNotLeaveEmptyProfiles() throws Exception {
        var patient = account("PATIENT");
        var other = account("PATIENT");
        save(patient, "Nguyen Van A", other.getEmail(), 400);
        save(patient, "    ", patient.getEmail(), 400);
        assertThat(profiles.findByAccount_Id(patient.getId())).isEmpty();
        assertThat(accounts.findById(patient.getId()).orElseThrow().getEmail()).isEqualTo(patient.getEmail());
    }

    @Test
    void doctorCannotCreatePatientProfile() throws Exception {
        var doctor = account("DOCTOR");
        mvc.perform(get(PATH).header("Authorization", token(doctor))).andExpect(status().isForbidden());
        save(doctor, "Nguyen Van A", doctor.getEmail(), 403);
        assertThat(profiles.findByAccount_Id(doctor.getId())).isEmpty();
    }

    @Test
    void twoFirstSavesCreateOnlyOneProfile() throws Exception {
        var patient = account("PATIENT");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Long> save = () -> {
            var principal = new CustomUserDetails(patient);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            try {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                service.updateMyProfile(new PatientProfileRequest("Nguyen Van A", null, null, patient.getEmail(), patient.getPhone(), null));
                return profiles.findByAccount_Id(patient.getId()).orElseThrow().getPatientProfileId();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        try {
            var first = pool.submit(save);
            var second = pool.submit(save);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private void save(Account account, String name, String email, int expected) throws Exception {
        mvc.perform(put(PATH + "/update").header("Authorization", token(account))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"fullName":"%s","email":"%s","phone":"0962964504","dateOfBirth":null,"gender":null}
                            """.formatted(name, email)))
                .andExpect(status().is(expected));
    }

    private String token(Account account) { return "Bearer " + jwt.generateToken(account.getUsername()); }
    private Account account(String role) {
        var account = new Account();
        String name = UUID.randomUUID().toString();
        account.setUsername(name); account.setPassword("unused"); account.setEmail(name + "@test.com");
        account.setPhone("0962964504"); account.setRole(roles.findByRoleName(role).orElseThrow());
        return accounts.saveAndFlush(account);
    }
}
