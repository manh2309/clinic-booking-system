CREATE TABLE weekly_schedules (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 doctor_id BIGINT NOT NULL,
 day_of_week INT NOT NULL,
 start_time TIME NOT NULL,
 end_time TIME NOT NULL,
 effective_from DATE NOT NULL,
 effective_to DATE NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_by VARCHAR(255), last_modified_by VARCHAR(255), created_date DATETIME(6), last_modified_date DATETIME(6),
 CONSTRAINT fk_weekly_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id),
 CONSTRAINT chk_weekly_day CHECK(day_of_week BETWEEN 1 AND 7),
 CONSTRAINT chk_weekly_time CHECK(start_time < end_time),
 CONSTRAINT chk_weekly_dates CHECK(effective_from <= effective_to)
);
CREATE INDEX idx_weekly_doctor ON weekly_schedules(doctor_id, active);
ALTER TABLE doctor_schedules ADD COLUMN weekly_schedule_id BIGINT;
ALTER TABLE doctor_schedules ADD CONSTRAINT fk_schedule_weekly FOREIGN KEY (weekly_schedule_id) REFERENCES weekly_schedules(id);
CREATE TABLE doctor_leaves (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 doctor_id BIGINT NOT NULL,
 start_at DATETIME(6) NOT NULL,
 end_at DATETIME(6) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL,
 reviewed_by BIGINT, reviewed_at DATETIME(6), review_reason VARCHAR(500),
 created_by VARCHAR(255), last_modified_by VARCHAR(255), created_date DATETIME(6), last_modified_date DATETIME(6),
 CONSTRAINT fk_leave_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id),
 CONSTRAINT chk_leave_time CHECK(start_at < end_at),
 CONSTRAINT chk_leave_status CHECK(status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'))
);
CREATE INDEX idx_leave_doctor_time ON doctor_leaves(doctor_id, status, start_at, end_at);
CREATE TABLE leave_contacts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 leave_id BIGINT NOT NULL,
 appointment_id BIGINT NOT NULL,
 contacted_at DATETIME(6), contacted_by BIGINT,
 CONSTRAINT uk_leave_appointment UNIQUE(leave_id,appointment_id),
 CONSTRAINT fk_contact_leave FOREIGN KEY (leave_id) REFERENCES doctor_leaves(id),
 CONSTRAINT fk_contact_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
);
