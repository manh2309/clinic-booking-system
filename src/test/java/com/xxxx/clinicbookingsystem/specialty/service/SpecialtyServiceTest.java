package com.xxxx.clinicbookingsystem.specialty.service;

import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.specialty.dto.UpdateSpecialtyStatusRequest;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialtyServiceTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private SpecialtyService service;

    @BeforeEach
    void setUp() {
        service = new SpecialtyService(specialtyRepository, doctorRepository);
    }

    @Test
    void updateStatusThrowsWhenSpecialtyDoesNotExist() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(1L, new UpdateSpecialtyStatusRequest(false)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));
    }

    @Test
    void updateStatusReturnsWithoutCheckingDoctorsWhenStatusIsUnchanged() {
        Specialty specialty = specialty(true);
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(specialty));

        service.updateStatus(1L, new UpdateSpecialtyStatusRequest(true));

        verify(doctorRepository, never()).existsBySpecialty_IdAndActiveTrue(1L);
        assertThat(specialty.getActive()).isTrue();
    }

    @Test
    void updateStatusRejectsDeactivationWhenActiveDoctorExists() {
        Specialty specialty = specialty(true);
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(specialty));
        when(doctorRepository.existsBySpecialty_IdAndActiveTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateStatus(1L, new UpdateSpecialtyStatusRequest(false)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SPECIALTY_HAS_ACTIVE_DOCTORS));
        assertThat(specialty.getActive()).isTrue();
    }

    @Test
    void updateStatusAllowsDeactivationWithoutActiveDoctorsAndReactivation() {
        Specialty specialty = specialty(true);
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(specialty));
        when(doctorRepository.existsBySpecialty_IdAndActiveTrue(1L)).thenReturn(false);

        service.updateStatus(1L, new UpdateSpecialtyStatusRequest(false));
        assertThat(specialty.getActive()).isFalse();

        service.updateStatus(1L, new UpdateSpecialtyStatusRequest(true));
        assertThat(specialty.getActive()).isTrue();
    }

    private Specialty specialty(boolean active) {
        Specialty specialty = new Specialty();
        specialty.setActive(active);
        return specialty;
    }
}
