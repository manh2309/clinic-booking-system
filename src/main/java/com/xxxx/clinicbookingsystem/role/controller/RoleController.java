package com.xxxx.clinicbookingsystem.role.controller;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import com.xxxx.clinicbookingsystem.role.dto.RoleResponse;
import com.xxxx.clinicbookingsystem.role.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping()
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.success(roleService.findAll());
    }
}
