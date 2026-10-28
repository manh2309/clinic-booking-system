CREATE TABLE reschedule_proposals (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 appointment_id BIGINT NOT NULL,
 target_slot_id BIGINT NOT NULL,
 pending_appointment_id BIGINT,
 replacement_appointment_id BIGINT,
 proposed_by BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 responded_at DATETIME(6),
 created_by VARCHAR(255), last_modified_by VARCHAR(255), created_date DATETIME(6), last_modified_date DATETIME(6),
 CONSTRAINT fk_reschedule_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id),
 CONSTRAINT fk_reschedule_slot FOREIGN KEY (target_slot_id) REFERENCES schedule_slots(slot_id),
 CONSTRAINT fk_reschedule_replacement FOREIGN KEY (replacement_appointment_id) REFERENCES appointments(appointment_id),
 CONSTRAINT fk_reschedule_admin FOREIGN KEY (proposed_by) REFERENCES accounts(account_id),
 CONSTRAINT uk_reschedule_pending UNIQUE(pending_appointment_id),
 CONSTRAINT chk_reschedule_status CHECK(status IN ('PENDING','ACCEPTED','DECLINED','WITHDRAWN','EXPIRED','INVALIDATED'))
);
CREATE INDEX idx_reschedule_appointment ON reschedule_proposals(appointment_id, id);
