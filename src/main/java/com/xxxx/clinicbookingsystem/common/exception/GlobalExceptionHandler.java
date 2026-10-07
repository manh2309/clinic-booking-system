package com.xxxx.clinicbookingsystem.common.exception;

import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getFieldError() != null
                ? exception.getFieldError().getDefaultMessage()
                : ErrorCode.INVALID_REQUEST.getMessage();

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), message));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        ErrorCode error = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.badRequest().body(ApiResponse.error(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity
                .status(ErrorCode.RESOURCE_CONFLICT.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.RESOURCE_CONFLICT.getCode(), ErrorCode.RESOURCE_CONFLICT.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        ErrorCode error = ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(error.getHttpStatus())
                .body(ApiResponse.error(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity
                .status(ErrorCode.UNCATEGORIZED_EXCEPTION.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.UNCATEGORIZED_EXCEPTION.getCode(),
                        ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage()
                ));
    }
}
