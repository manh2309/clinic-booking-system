package com.xxxx.clinicbookingsystem.specialty.service;

import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.specialty.dto.CreateSpecialtyRequest;
import com.xxxx.clinicbookingsystem.specialty.dto.SpecialtyResponse;
import com.xxxx.clinicbookingsystem.specialty.dto.UpdateSpecialtyRequest;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecialtyService {
    private final SpecialtyRepository repository;

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> findAll() {
        return repository.findAllByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public SpecialtyResponse create(CreateSpecialtyRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) throw new AppException(ErrorCode.SPECIALTY_EXISTED);
        Specialty specialty = new Specialty();
        specialty.setName(name);
        specialty.setDescription(request.description());
        return toResponse(repository.save(specialty));
    }

    public Specialty getActive(Long id) {
        return repository.findByIdAndActiveTrue(id).orElseThrow(() -> new AppException(ErrorCode.SPECIALTY_NOT_FOUND));
    }

    @Transactional
    public SpecialtyResponse update(Long id, UpdateSpecialtyRequest request) {
        Specialty specialty = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.SPECIALTY_NOT_FOUND));
        String name = request.name().trim();
        if(repository.existsByNameIgnoreCaseAndIdNot(name, specialty.getId())) {
            throw new AppException(ErrorCode.SPECIALTY_EXISTED);
        }

        specialty.setName(name);
        specialty.setDescription(request.description());
        Specialty saveSpecialty = repository.save(specialty);
        return toResponse(saveSpecialty);
    }
    private SpecialtyResponse toResponse(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getName(), specialty.getDescription());
    }
}
