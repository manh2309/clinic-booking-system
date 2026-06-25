package com.xxxx.clinicbookingsystem.role.service.impl;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.role.dto.RoleResponse;
import com.xxxx.clinicbookingsystem.role.entity.Role;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.role.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<RoleResponse> findAll() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream().map(role -> new RoleResponse(
                role.getId(),
                role.getRoleName()
        )).toList();
    }
}
