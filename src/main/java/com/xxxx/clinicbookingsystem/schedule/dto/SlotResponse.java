package com.xxxx.clinicbookingsystem.schedule.dto;

import java.time.LocalDateTime;
import com.xxxx.clinicbookingsystem.schedule.entity.SlotStatus;

public record SlotResponse(Long id, LocalDateTime startAt, LocalDateTime endAt, SlotStatus status, String blockReason) {}
