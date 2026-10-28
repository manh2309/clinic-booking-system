package com.xxxx.clinicbookingsystem.appointment.visit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VisitEventRepository extends JpaRepository<VisitEvent,Long> {
    List<VisitEvent> findByVisitIdOrderByIdAsc(Long visitId);
}
