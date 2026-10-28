CREATE TABLE clinic_visits (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 appointment_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL,
 active_doctor_id BIGINT,
 checked_in_at DATETIME(6) NOT NULL,
 checked_in_by BIGINT NOT NULL,
 priority_at DATETIME(6) NOT NULL,
 started_at DATETIME(6),
 completed_at DATETIME(6),
 canceled_at DATETIME(6),
 cancellation_reason VARCHAR(500),
 created_by VARCHAR(255), last_modified_by VARCHAR(255), created_date DATETIME(6), last_modified_date DATETIME(6),
 CONSTRAINT uk_visit_appointment UNIQUE(appointment_id),
 CONSTRAINT uk_visit_active_doctor UNIQUE(active_doctor_id),
 CONSTRAINT fk_visit_appointment FOREIGN KEY(appointment_id) REFERENCES appointments(appointment_id),
 CONSTRAINT fk_visit_active_doctor FOREIGN KEY(active_doctor_id) REFERENCES doctors(doctor_id),
 CONSTRAINT fk_visit_receptionist FOREIGN KEY(checked_in_by) REFERENCES accounts(account_id),
 CONSTRAINT chk_visit_status CHECK(status IN ('WAITING','IN_PROGRESS','COMPLETED','CANCELED')),
 CONSTRAINT chk_visit_active CHECK((status='IN_PROGRESS' AND active_doctor_id IS NOT NULL) OR (status<>'IN_PROGRESS' AND active_doctor_id IS NULL))
);
CREATE INDEX idx_visit_queue ON clinic_visits(status,priority_at,id);
CREATE TABLE clinic_visit_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 visit_id BIGINT NOT NULL,
 action VARCHAR(30) NOT NULL,
 actor_id BIGINT NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 reason VARCHAR(500),
 CONSTRAINT fk_visit_event_visit FOREIGN KEY(visit_id) REFERENCES clinic_visits(id),
 CONSTRAINT fk_visit_event_actor FOREIGN KEY(actor_id) REFERENCES accounts(account_id)
);
CREATE INDEX idx_visit_event_history ON clinic_visit_events(visit_id,id);
