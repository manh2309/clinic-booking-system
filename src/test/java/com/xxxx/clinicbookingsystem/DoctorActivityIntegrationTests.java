package com.xxxx.clinicbookingsystem;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.auth.jwt.JwtService;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.entity.DoctorSchedule;
import com.xxxx.clinicbookingsystem.schedule.entity.ScheduleSlot;
import com.xxxx.clinicbookingsystem.schedule.entity.SlotStatus;
import com.xxxx.clinicbookingsystem.schedule.repository.ScheduleRepository;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorActivityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired AccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired DoctorRepository doctors;
    @Autowired SpecialtyRepository specialties;
    @Autowired ScheduleRepository schedules;
    @Autowired AppointmentRepository appointments;

    @ParameterizedTest
    @CsvSource({"false,true,true", "true,false,true", "true,true,false", "true,true,true"})
    void publicAvailabilityAndBookingAgree(boolean doctorActive, boolean accountActive,
                                          boolean specialtyActive) throws Exception {
        var f = fixture(doctorActive, accountActive, specialtyActive);
        boolean available = doctorActive && accountActive && specialtyActive;
        mvc.perform(get("/api/v1/doctors").param("specialtyId", f.specialtyId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(available ? 1 : 0));
        mvc.perform(get("/api/v1/doctors/" + f.doctorId()))
                .andExpect(status().is(available ? 200 : 404));
        var slots = mvc.perform(get("/api/v1/doctors/" + f.doctorId() + "/available-slots")
                .param("date", f.start().toLocalDate().toString()));
        if (available) {
            slots.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(f.slotId()));
        } else {
            slots.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2007));
        }
        mvc.perform(post("/api/v1/appointments").header("Authorization", f.patientToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + f.slotId() + "}"))
                .andExpect(status().is(available ? 200 : 409));
    }

    @Test
    void inactiveDoctorCanReadButCannotWrite() throws Exception {
        var f = fixture(false, true, true);
        mvc.perform(get("/api/v1/doctors/me").header("Authorization", f.doctorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(get("/api/v1/doctors/me/appointments").header("Authorization", f.doctorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(f.appointmentId()));
        mvc.perform(get("/api/v1/doctors/me/schedules").header("Authorization", f.doctorToken()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/doctors/me/schedules").header("Authorization", f.doctorToken())
                        .contentType(MediaType.APPLICATION_JSON).content(scheduleBody(f.start().plusDays(1))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/doctors/me/schedules/" + f.scheduleId())
                        .header("Authorization", f.doctorToken()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/doctors/me/appointments/" + f.appointmentId() + "/status")
                        .header("Authorization", f.doctorToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2007));
    }

    @Test
    void lockingAccountInvalidatesPreviouslyIssuedToken() throws Exception {
        var f = fixture(true, true, true);
        var account = accounts.findById(f.accountId()).orElseThrow();
        account.setIsActive(false);
        accounts.saveAndFlush(account);
        for (String path : new String[]{"/api/v1/doctors/me", "/api/v1/doctors/me/appointments",
                "/api/v1/doctors/me/schedules"}) {
            mvc.perform(get(path).header("Authorization", f.doctorToken()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void selfProfileRequiresDoctorRoleAndManualScheduleCreationIsRetired() throws Exception {
        var f = fixture(true, true, true);
        mvc.perform(get("/api/v1/doctors/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctors/me").header("Authorization", f.patientToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/doctors/me/schedules").header("Authorization", f.doctorToken())
                        .contentType(MediaType.APPLICATION_JSON).content(scheduleBody(f.start().plusDays(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveScheduleDoesNotPublishAvailableSlots() throws Exception {
        var f = fixture(true, true, true);
        var schedule = schedules.findById(f.scheduleId()).orElseThrow();
        schedule.setActive(false);
        schedules.saveAndFlush(schedule);
        mvc.perform(get("/api/v1/doctors/" + f.doctorId() + "/available-slots")
                        .param("date", f.start().toLocalDate().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    private Fixture fixture(boolean doctorActive, boolean accountActive, boolean specialtyActive) {
        String suffix = UUID.randomUUID().toString();
        Account doctorAccount = account("d_" + suffix, "DOCTOR", accountActive);
        Account patient = account("p_" + suffix, "PATIENT", true);
        Specialty specialty = new Specialty();
        specialty.setName("Activity " + suffix);
        specialty.setActive(specialtyActive);
        specialty = specialties.saveAndFlush(specialty);
        Doctor doctor = new Doctor();
        doctor.setAccount(doctorAccount);
        doctor.setSpecialty(specialty);
        doctor.setFullName("Doctor Activity");
        doctor.setActive(doctorActive);
        doctor = doctors.saveAndFlush(doctor);
        LocalDateTime start = LocalDateTime.now().plusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0);
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setStartAt(start);
        schedule.setEndAt(start.plusHours(1));
        ScheduleSlot free = new ScheduleSlot();
        free.setStartAt(start);
        free.setEndAt(start.plusMinutes(30));
        schedule.addSlot(free);
        ScheduleSlot booked = new ScheduleSlot();
        booked.setStartAt(start.plusMinutes(30));
        booked.setEndAt(start.plusHours(1));
        booked.setStatus(SlotStatus.BOOKED);
        schedule.addSlot(booked);
        schedule = schedules.saveAndFlush(schedule);
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(booked);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment = appointments.saveAndFlush(appointment);
        return new Fixture(doctor.getId(), doctorAccount.getId(), specialty.getId(), schedule.getId(),
                free.getId(), appointment.getId(), start, "Bearer " + jwt.generateToken(doctorAccount.getUsername()),
                "Bearer " + jwt.generateToken(patient.getUsername()));
    }

    private Account account(String username, String role, boolean active) {
        Account account = new Account();
        account.setUsername(username);
        account.setEmail(username + "@example.com");
        account.setPassword("unused-test-hash");
        account.setRole(roles.findByRoleName(role).orElseThrow());
        account.setIsActive(active);
        return accounts.saveAndFlush(account);
    }

    private String scheduleBody(LocalDateTime start) {
        return "{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusHours(1) + "\"}";
    }

    private record Fixture(Long doctorId, Long accountId, Long specialtyId, Long scheduleId,
                           Long slotId, Long appointmentId, LocalDateTime start,
                           String doctorToken, String patientToken) {}
}
