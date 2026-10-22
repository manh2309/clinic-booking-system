package com.xxxx.clinicbookingsystem.notification.repository;

import com.xxxx.clinicbookingsystem.notification.entity.Notification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientId(Long accountId, Pageable pageable);
    long countByRecipientIdAndReadFalse(Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.recipient.id = :accountId AND n.read = false")
    int markAllRead(Long accountId, java.time.LocalDateTime readAt);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.recipient.id = :accountId")
    Optional<Notification> lockOwned(Long id, Long accountId);
}
