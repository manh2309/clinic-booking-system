package com.xxxx.clinicbookingsystem.schedule.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScheduleResponse(Long id, LocalDateTime startAt, LocalDateTime endAt, List<SlotResponse> slots) {}
