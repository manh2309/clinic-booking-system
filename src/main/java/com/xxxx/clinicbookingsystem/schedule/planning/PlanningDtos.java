package com.xxxx.clinicbookingsystem.schedule.planning;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class PlanningDtos  {
    private PlanningDtos()  {
    }
    public record WeeklyRequest(@Min(1) @Max(7) int dayOfWeek,@NotNull LocalTime startTime,@NotNull LocalTime endTime,@NotNull LocalDate effectiveFrom,@NotNull LocalDate effectiveTo)  {
    }
    public record TimeWindow(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    public record WeeklyBatchRequest(
            @NotEmpty @Size(max=7) Set<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
            @NotEmpty @Size(max=8) List<@NotNull @jakarta.validation.Valid TimeWindow> windows,
            @NotNull LocalDate effectiveFrom, @NotNull LocalDate effectiveTo) {}
    public record WeeklyBatchResult(int added, int skipped, int created, List<String> conflicts) {}
    public record WeeklyResponse(Long id,int dayOfWeek,LocalTime startTime,LocalTime endTime,LocalDate effectiveFrom,LocalDate effectiveTo,boolean active)  {
    }
    public record LeaveRequest(@NotNull LocalDateTime startAt,@NotNull LocalDateTime endAt,@NotBlank @Size(max=500) String reason)  {
    }
    public record ReviewRequest(@NotBlank String decision, boolean confirmCancellation,@NotBlank @Size(max=500) String reason)  {
    }
    public record LeaveResponse(Long id,Long doctorId,String doctorName,LocalDateTime startAt,LocalDateTime endAt,String reason,String status,Long reviewedBy,LocalDateTime reviewedAt,String reviewReason)  {
    }
    public record Affected(Long appointmentId,Long patientAccountId,String patientName,String patientPhone,LocalDateTime startAt,String status,Long contactId,LocalDateTime contactedAt)  {
    }
    public record GenerationResult(int created,List<String> conflicts)  {
    }
}
