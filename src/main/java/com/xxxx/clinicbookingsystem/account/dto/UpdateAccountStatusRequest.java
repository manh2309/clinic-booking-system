package com.xxxx.clinicbookingsystem.account.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        @NotNull(message = "Trạng thái tài khoản không được bỏ trống")
        Boolean isActive
) {}
