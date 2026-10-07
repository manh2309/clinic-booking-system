-- MySQL 8; select clinic_booking as the active schema when running manually.
-- CREATE IF NOT EXISTS also lets Flyway register V2 after this exact script
-- was run manually. It does not validate a pre-existing table's structure.

CREATE TABLE IF NOT EXISTS patient_profiles (
    patient_profile_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    full_name VARCHAR(100),
    date_of_birth DATE,
    gender VARCHAR(20),
    address VARCHAR(500),
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    created_date DATETIME(6),
    last_modified_date DATETIME(6),
    PRIMARY KEY (patient_profile_id),
    UNIQUE KEY uk_patient_profile_account (account_id),
    CONSTRAINT fk_patient_profile_account
        FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT chk_patient_profile_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One result record per appointment; patient and doctor are obtained
-- through appointments rather than duplicated in this table.
CREATE TABLE IF NOT EXISTS medical_records (
    medical_record_id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    diagnosis TEXT NOT NULL,
    examination_notes TEXT,
    treatment_advice TEXT,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    created_date DATETIME(6),
    last_modified_date DATETIME(6),
    PRIMARY KEY (medical_record_id),
    UNIQUE KEY uk_medical_record_appointment (appointment_id),
    CONSTRAINT fk_medical_record_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Each row belongs to one recipient; create separate rows for patient
-- and doctor when the same booking event should notify both.
CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_account_id BIGINT NOT NULL,
    appointment_id BIGINT,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at DATETIME(6),
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    created_date DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    last_modified_date DATETIME(6),
    PRIMARY KEY (notification_id),
    KEY idx_notification_recipient_created
        (recipient_account_id, created_date, notification_id),
    KEY idx_notification_recipient_unread
        (recipient_account_id, is_read),
    KEY idx_notification_appointment (appointment_id),
    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_notification_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id),
    CONSTRAINT chk_notification_type CHECK (
        type IN ('APPOINTMENT_BOOKED', 'APPOINTMENT_CONFIRMED',
                 'APPOINTMENT_CANCELED', 'APPOINTMENT_COMPLETED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Role/ownership checks, valid appointment status, future birth dates,
-- and read_at/is_read consistency must also be validated in the service.
-- Phone/email remain in accounts; no existing rows are changed here.
