package com.xxxx.clinicbookingsystem.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ErrorCode error = ErrorCode.ACCESS_DENIED;
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error.getCode(), error.getMessage()));
    }
}
