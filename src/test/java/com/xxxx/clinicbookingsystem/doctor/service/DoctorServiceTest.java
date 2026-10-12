package com.xxxx.clinicbookingsystem.doctor.service;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.doctor.dto.DoctorResponse;
import com.xxxx.clinicbookingsystem.doctor.dto.UpdateDoctorRequest;
import com.xxxx.clinicbookingsystem.doctor.dto.UpdateDoctorStatusRequest;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.service.SpecialtyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static java.util.List.of;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private SpecialtyService specialtyService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AppointmentRepository appointmentRepository;

    private DoctorService service;

    @BeforeEach
    void setUp() {
        service = new DoctorService(
                doctorRepository,
                accountRepository,
                roleRepository,
                specialtyService,
                passwordEncoder,
                appointmentRepository
        );
    }

    @Test
    void updateThrowsWhenDoctorDoesNotExist() {
        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("Nguyễn Văn Bình", 1L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DOCTOR_NOT_FOUND));
        verify(specialtyService, never()).getActive(1L);
    }

    @Test
    void updateRejectsNameShorterThanFourCharactersAfterTrim() {
        Doctor doctor = doctor(false);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.update(1L, request("  An  ", 2L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(specialtyService, never()).getActive(2L);
    }

    @Test
    void updateUsesOnlyAnActiveSpecialtyAndPropagatesSpecialtyError() {
        Doctor doctor = doctor(true);
        UpdateDoctorRequest request = request("Nguyễn Văn Bình", 2L);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(specialtyService.getActive(2L)).thenThrow(new AppException(ErrorCode.SPECIALTY_NOT_FOUND));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));
    }

    @Test
    void updateChangesProfileButPreservesAccountAndInactiveStatus() {
        Doctor doctor = doctor(false);
        Account originalAccount = doctor.getAccount();
        Specialty specialty = new Specialty();
        specialty.setId(2L);
        specialty.setName("Tim mạch");
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(specialtyService.getActive(2L)).thenReturn(specialty);

        DoctorResponse response = service.update(
                1L,
                new UpdateDoctorRequest("  Nguyễn Văn Bình  ", null, null, 2L)
        );

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn Bình");
        assertThat(response.qualification()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.specialtyId()).isEqualTo(2L);
        assertThat(doctor.getAccount()).isSameAs(originalAccount);
        assertThat(doctor.getActive()).isFalse();
        verify(doctorRepository, never()).save(doctor);
    }

    @Test
    void updateStatusThrowsWhenDoctorDoesNotExist() {
        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, new UpdateDoctorStatusRequest(false)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DOCTOR_NOT_FOUND));
    }

    @Test
    void updateStatusReturnsWithoutFurtherChecksWhenStatusIsUnchanged() {
        Doctor doctor = doctor(false);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

        service.updateStatus(1L, new UpdateDoctorStatusRequest(false));

        verify(appointmentRepository, never()).existsByDoctor_IdAndStatusIn(1L, of(
                AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED
        ));
        verify(specialtyService, never()).getActive(doctor.getSpecialty().getId());
    }

    @Test
    void updateStatusRejectsDeactivationWhenUnresolvedAppointmentExists() {
        Doctor doctor = doctor(true);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctor_IdAndStatusIn(1L, of(
                AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED
        ))).thenReturn(true);

        assertThatThrownBy(() -> service.updateStatus(1L, new UpdateDoctorStatusRequest(false)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DOCTOR_HAS_PENDING_APPOINTMENTS));
        assertThat(doctor.getActive()).isTrue();
    }

    @Test
    void updateStatusDeactivatesDoctorWithoutUnresolvedAppointments() {
        Doctor doctor = doctor(true);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctor_IdAndStatusIn(1L, of(
                AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED
        ))).thenReturn(false);

        service.updateStatus(1L, new UpdateDoctorStatusRequest(false));

        assertThat(doctor.getActive()).isFalse();
    }

    @Test
    void updateStatusOnlyReactivatesDoctorWhenSpecialtyIsActive() {
        Doctor doctor = doctor(false);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(specialtyService.getActive(1L)).thenThrow(new AppException(ErrorCode.SPECIALTY_NOT_FOUND));

        assertThatThrownBy(() -> service.updateStatus(1L, new UpdateDoctorStatusRequest(true)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));
        assertThat(doctor.getActive()).isFalse();

        doReturn(doctor.getSpecialty()).when(specialtyService).getActive(1L);
        service.updateStatus(1L, new UpdateDoctorStatusRequest(true));
        assertThat(doctor.getActive()).isTrue();
    }

    private UpdateDoctorRequest request(String fullName, Long specialtyId) {
        return new UpdateDoctorRequest(fullName, "Thạc sĩ, Bác sĩ", "Có 10 năm kinh nghiệm", specialtyId);
    }

    private Doctor doctor(boolean active) {
        Specialty specialty = new Specialty();
        specialty.setId(1L);
        specialty.setName("Nội khoa");

        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setAccount(new Account());
        doctor.setSpecialty(specialty);
        doctor.setFullName("Tên ban đầu");
        doctor.setQualification("Bác sĩ");
        doctor.setDescription("Mô tả ban đầu");
        doctor.setActive(active);
        return doctor;
    }
}
