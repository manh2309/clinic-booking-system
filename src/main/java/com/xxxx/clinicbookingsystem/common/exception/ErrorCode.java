package com.xxxx.clinicbookingsystem.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_REQUEST(1001, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1002, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(1003, "Access denied", HttpStatus.FORBIDDEN),
    RESOURCE_CONFLICT(1004, "Resource conflict", HttpStatus.CONFLICT),
    ACCOUNT_NOT_FOUND(1101, "Account not found", HttpStatus.NOT_FOUND),
    USERNAME_EXISTED(1102, "Username already exists", HttpStatus.BAD_REQUEST),
    EMAIL_EXISTED(1103, "Email already exists", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(1201, "Role not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(1104, "Invalid username or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE(1105, "Account is inactive", HttpStatus.FORBIDDEN),
    CANNOT_LOCK_SELF(1106, "Không thể tự khóa tài khoản của mình", HttpStatus.BAD_REQUEST),
    DOCTOR_NOT_FOUND(2001, "Doctor not found", HttpStatus.NOT_FOUND),
    PATIENT_NOT_FOUND(2002, "Patient not found", HttpStatus.NOT_FOUND),
    APPOINTMENT_NOT_FOUND(3001, "Appointment not found", HttpStatus.NOT_FOUND),
    APPOINTMENT_DUPLICATED(3002, "Appointment is duplicated", HttpStatus.BAD_REQUEST),
    SCHEDULE_NOT_AVAILABLE(3003, "Schedule is not available", HttpStatus.BAD_REQUEST),
    SPECIALTY_NOT_FOUND(2003, "Specialty not found", HttpStatus.NOT_FOUND),
    SPECIALTY_EXISTED(2004, "Specialty already exists", HttpStatus.CONFLICT),
    SCHEDULE_OVERLAPPED(3004, "Schedule overlaps an existing schedule", HttpStatus.CONFLICT),
    INVALID_SCHEDULE_TIME(3005, "Schedule time is invalid", HttpStatus.BAD_REQUEST),
    SLOT_NOT_FOUND(3006, "Schedule slot not found", HttpStatus.NOT_FOUND),
    SLOT_ALREADY_BOOKED(3007, "Schedule slot is already booked", HttpStatus.CONFLICT),
    INVALID_APPOINTMENT_STATUS(3008, "Invalid appointment status transition", HttpStatus.BAD_REQUEST),
    PATIENT_ACCOUNT_NOT_FOUND(4001, "Patient find by account not found", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
