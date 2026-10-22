package com.xxxx.clinicbookingsystem.appointment.dto;

public record DoctorAppointmentSummary(long totalAppointments, long pendingCount, long confirmedCount,
                                       long completedCount, long openScheduleCount) {}
