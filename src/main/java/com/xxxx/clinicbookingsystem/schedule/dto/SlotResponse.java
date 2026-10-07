package com.xxxx.clinicbookingsystem.schedule.dto;

import java.time.LocalDateTime;

public record SlotResponse(Long id, LocalDateTime startAt, LocalDateTime endAt) {}
