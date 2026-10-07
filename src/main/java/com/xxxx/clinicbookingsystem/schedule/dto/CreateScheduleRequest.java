package com.xxxx.clinicbookingsystem.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateScheduleRequest(@NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt) {}
