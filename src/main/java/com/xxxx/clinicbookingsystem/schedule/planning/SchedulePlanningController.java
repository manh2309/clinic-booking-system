package com.xxxx.clinicbookingsystem.schedule.planning;
import com.xxxx.clinicbookingsystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import static com.xxxx.clinicbookingsystem.schedule.planning.PlanningDtos.*;
@RestController @RequiredArgsConstructor
public class SchedulePlanningController  {
    private final SchedulePlanningService service;
    @GetMapping("/api/v1/admin/doctors/{doctorId}/weekly-schedules") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<WeeklyResponse>> weekly(@PathVariable Long doctorId) {
        return ApiResponse.success(service.weekly(doctorId));
    }
    @PostMapping("/api/v1/admin/doctors/{doctorId}/weekly-schedules") @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<GenerationResult> add(@PathVariable Long doctorId,@Valid @RequestBody WeeklyRequest request) {
        service.addWeekly(doctorId,request);
        return ApiResponse.success(service.generate(doctorId));
    }
    @PostMapping("/api/v1/admin/doctors/{doctorId}/weekly-schedules/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<WeeklyBatchResult> addBatch(@PathVariable Long doctorId, @Valid @RequestBody WeeklyBatchRequest request) {
        return ApiResponse.success(service.addWeeklyBatch(doctorId, request));
    }

    @PostMapping("/api/v1/admin/doctors/{doctorId}/schedules/generate") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GenerationResult> generate(@PathVariable Long doctorId) {
        return ApiResponse.success(service.generate(doctorId));
    }
    @PatchMapping("/api/v1/admin/doctors/{doctorId}/weekly-schedules/{id}/end") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> end(@PathVariable Long doctorId,@PathVariable Long id,@RequestParam LocalDate from) {
        service.endWeekly(doctorId,id,from);
        return ApiResponse.success();
    }
    @GetMapping("/api/v1/doctors/me/leaves") @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<LeaveResponse>> mine() {
        return ApiResponse.success(service.mine());
    }
    @PostMapping("/api/v1/doctors/me/leaves") @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<LeaveResponse> request(@Valid @RequestBody LeaveRequest request) {
        return ApiResponse.success(service.request(request));
    }
    @PatchMapping("/api/v1/doctors/me/leaves/{id}/withdraw") @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<LeaveResponse> withdraw(@PathVariable Long id) {
        return ApiResponse.success(service.withdraw(id));
    }
    @GetMapping("/api/v1/admin/leaves") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<LeaveResponse>> all(@RequestParam(required=false) Long doctorId) {
        return ApiResponse.success(service.listLeaves(doctorId));
    }
    @GetMapping("/api/v1/admin/leaves/{id}/affected") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Affected>> affected(@PathVariable Long id) {
        return ApiResponse.success(service.affected(id));
    }
    @PatchMapping("/api/v1/admin/leaves/{id}/review") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LeaveResponse> review(@PathVariable Long id,@Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(service.review(id,request));
    }
    @PatchMapping("/api/v1/admin/leaves/{id}/contacts/{contactId}") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> contact(@PathVariable Long id,@PathVariable Long contactId) {
        service.contact(id,contactId);
        return ApiResponse.success();
    }
}
