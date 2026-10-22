package com.xxxx.clinicbookingsystem.appointment.job;

import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "clinic.appointments.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class AppointmentExpiryJob {
    private final AppointmentService service;

    @Scheduled(fixedDelayString = "${clinic.appointments.expiry-delay-ms:60000}")
    public void expirePending() {
        for (Long id : service.duePendingIds()) {
            try {
                // Mỗi lịch một transaction, tránh giữ khóa cả lô trong lúc chạy job.
                service.expirePending(id);
            } catch (RuntimeException exception) {
                log.warn("Không thể hết hạn lịch hẹn {}", id, exception);
            }
        }
    }
}
