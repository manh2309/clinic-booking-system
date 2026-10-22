package com.xxxx.clinicbookingsystem.schedule.dto;

import java.time.LocalDate;

public record AvailableDateResponse(LocalDate date, long availableSlots) {}
