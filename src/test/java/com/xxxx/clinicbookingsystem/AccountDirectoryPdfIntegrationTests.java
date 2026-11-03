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
class AccountDirectoryPdfIntegrationTests {
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
    @Autowired com.xxxx.clinicbookingsystem.appointment.visit.VisitRepository visits;
    @Autowired JwtService jwt;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @AfterEach void clearAuthentication(){SecurityContextHolder.clearContext();}

    @Test void passwordChangeRevokesOldTokensAndKeepsOtherAccounts() throws Exception {
        var a=account("DOCTOR");a.setPassword(encoder.encode("OldPassword123!"));accounts.saveAndFlush(a);
        var old=token(a);var other=account("PATIENT");var otherToken=token(other);
        mvc.perform(post("/api/v1/me/account/password").header("Authorization",old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/account").header("Authorization",old)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/account/password").header("Authorization",old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/account").header("Authorization",old)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/account").header("Authorization",otherToken)).andExpect(status().isOk());
        var updated=accounts.findById(a.getId()).orElseThrow();assertThat(updated.getTokenVersion()).isEqualTo(1);
        assertThat(encoder.matches("NewPassword123!",updated.getPassword())).isTrue();
        mvc.perform(get("/api/v1/me/account").header("Authorization","Bearer "+jwt.generateToken(updated.getUsername(),updated.getTokenVersion()))).andExpect(status().isOk());
    }
    @Test void directorySearchFiltersBeforePaginationAndRestrictsRole() throws Exception {
        var admin=account("ADMIN");var patient=account("PATIENT");String q="lookup"+UUID.randomUUID().toString().replace("-","");
        for(int i=0;i<3;i++){var a=account("PATIENT");a.setUsername(q+i);a.setIsActive(i!=2);accounts.saveAndFlush(a);}
        mvc.perform(get("/api/v1/admin/directory/accounts").header("Authorization",token(admin)).param("keyword",q).param("active","true").param("size","1").param("page","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get("/api/v1/admin/directory/doctors").header("Authorization",token(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/directory/specialties").header("Authorization",token(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/directory/accounts").header("Authorization",token(patient))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/directory/accounts").header("Authorization",token(admin)).param("size","101")).andExpect(status().isBadRequest());
    }
    @Test void pdfIsPrivateEmbedsVietnameseAndPaginatesLongResult() throws Exception {
        var f=fixture(now().minusMinutes(10));var a=appointment(f,AppointmentStatus.COMPLETED);
        var r=new com.xxxx.clinicbookingsystem.medical.entity.MedicalRecord();r.setAppointment(a);r.setDiagnosis("Kết quả kiểm thử tiếng Việt: Nguyễn Minh An");
        r.setExaminationNotes("Nội dung kiểm thử xuống dòng và phân trang. ".repeat(110));r.setTreatmentAdvice("Tái khám theo lịch hẹn.");records.saveAndFlush(r);
        String path="/api/v1/patient/me/medical-records/"+a.getId()+"/pdf";
        byte[] bytes=mvc.perform(get(path).header("Authorization",token(f.patient()))).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)){
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("PHIẾU KẾT QUẢ KHÁM","Nguyễn Minh An","Tái khám theo lịch hẹn.");
            for(var page:pdf.getPages())for(var name:page.getResources().getFontNames())assertThat(page.getResources().getFont(name).isEmbedded()).isTrue();
            var dir=java.nio.file.Path.of("target/pdf-qa");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("ket-qua-kiem-thu.pdf"),bytes);
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(pdf);
            for(int i=0;i<pdf.getNumberOfPages();i++)javax.imageio.ImageIO.write(renderer.renderImageWithDPI(i,110),"PNG",dir.resolve("page-"+(i+1)+".png").toFile());
        }
        mvc.perform(get(path).header("Authorization",token(account("PATIENT")))).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctors/me/appointments/"+a.getId()+"/medical-record/pdf").header("Authorization",token(f.doctorAccount()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/doctors/me/appointments/"+a.getId()+"/medical-record/pdf").header("Authorization",token(account("DOCTOR")))).andExpect(status().isNotFound());
    }
    @Test void appointmentSearchUsesPatientNamePhoneDateAndOwnership() throws Exception {
        var f=fixture(now().plusHours(2));var a=appointment(f,AppointmentStatus.CONFIRMED);var admin=account("ADMIN");
        mvc.perform(get("/api/v1/admin/appointments").header("Authorization",token(admin)).param("keyword",f.patient().getUsername()).param("from",now().toLocalDate().toString()).param("to",now().toLocalDate().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/doctors/me/appointments").header("Authorization",token(f.doctorAccount())).param("keyword",f.patient().getUsername()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        var other=fixture(now().plusHours(3));
        mvc.perform(get("/api/v1/doctors/me/appointments").header("Authorization",token(other.doctorAccount())).param("keyword",f.patient().getUsername()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }
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
        a.setSlot(slot); appointments.saveAndFlush(a);
        if (status == AppointmentStatus.CONFIRMED && !slot.getStartAt().isAfter(now())) {
            var visit = new com.xxxx.clinicbookingsystem.appointment.visit.ClinicVisit();
            visit.setAppointment(a); visit.setStatus(com.xxxx.clinicbookingsystem.appointment.visit.ClinicVisit.Status.IN_PROGRESS);
            visit.setActiveDoctorId(f.doctor().getId()); visit.setCheckedInBy(f.patient().getId());
            visit.setCheckedInAt(slot.getStartAt()); visit.setStartedAt(slot.getStartAt()); visit.setPriorityAt(slot.getStartAt());
            visits.saveAndFlush(visit);
        }
        return a;
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

