package com.xxxx.clinicbookingsystem.notification.service;

import com.xxxx.clinicbookingsystem.appointment.entity.Appointment;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.common.response.PageResponse;
import com.xxxx.clinicbookingsystem.notification.dto.NotificationResponse;
import com.xxxx.clinicbookingsystem.notification.entity.*;
import com.xxxx.clinicbookingsystem.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    private final CurrentAccount currentAccount;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentChanged(Appointment appointment, NotificationType type) {
        String label = switch (type) {
            case APPOINTMENT_BOOKED -> "Đã đặt lịch khám";
            case APPOINTMENT_CONFIRMED -> "Lịch khám đã được xác nhận";
            case APPOINTMENT_CANCELED -> "Lịch khám đã bị hủy";
            case APPOINTMENT_COMPLETED -> "Đã có kết quả khám";
            case APPOINTMENT_EXPIRED -> "Lịch khám đã hết hạn xác nhận";
            case APPOINTMENT_NO_SHOW -> "Đã ghi nhận vắng mặt";
        };
        // Không đưa chẩn đoán vào thông báo; kết quả chỉ đọc qua luồng có kiểm tra chủ sở hữu.
        for (var recipient : java.util.List.of(appointment.getPatient(), appointment.getDoctor().getAccount())) {
            var notification = new Notification();
            notification.setRecipient(recipient); notification.setAppointment(appointment);
            notification.setType(type); notification.setTitle(label);
            notification.setMessage("Lịch hẹn #" + appointment.getId() + " • " + appointment.getSlot().getStartAt().toLocalDate()
                    + " " + appointment.getSlot().getStartAt().toLocalTime() + ". " + label + ".");
            repository.save(notification);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(int page, int size) {
        return PageResponse.from(repository.findByRecipientId(currentAccount.id(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate", "id"))).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount() { return repository.countByRecipientIdAndReadFalse(currentAccount.id()); }

    @Transactional
    public NotificationResponse markRead(Long id) {
        var notification = repository.lockOwned(id, currentAccount.id())
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isRead()) {
            notification.setRead(true); notification.setReadAt(LocalDateTime.now(clock));
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentAccount.id(), LocalDateTime.now(clock));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getAppointment() == null ? null : n.getAppointment().getId(),
                n.getType(), n.getTitle(), n.getMessage(), n.isRead(), n.getReadAt(), n.getCreatedDate());
    }
}
