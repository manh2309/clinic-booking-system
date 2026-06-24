package com.xxxx.clinicbookingsystem.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error",HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_REQUEST(1001, "Invalid request", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND(1002, "Account not found", HttpStatus.NOT_FOUND),
    USERNAME_EXISTED(1003, "Username already exists", HttpStatus.BAD_REQUEST),
    EMAIL_EXISTED(1004, "Email already exists", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(1005, "Role not found", HttpStatus.NOT_FOUND),

    DOCTOR_NOT_FOUND(2001, "Doctor not found", HttpStatus.NOT_FOUND),
    PATIENT_NOT_FOUND(2002, "Patient not found", HttpStatus.NOT_FOUND),
    APPOINTMENT_NOT_FOUND(3001, "Appointment not found", HttpStatus.NOT_FOUND),
    APPOINTMENT_DUPLICATED(3002, "Appointment is duplicated", HttpStatus.BAD_REQUEST),
    SCHEDULE_NOT_AVAILABLE(3003, "Schedule is not available", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
