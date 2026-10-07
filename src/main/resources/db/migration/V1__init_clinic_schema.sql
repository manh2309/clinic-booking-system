CREATE TABLE IF NOT EXISTS roles (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (role_id), UNIQUE KEY uk_role_name (role_name)
);

CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL, password VARCHAR(255) NOT NULL,
    email VARCHAR(100), phone VARCHAR(20), is_active BOOLEAN NOT NULL DEFAULT TRUE,
    role_id BIGINT NOT NULL,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (account_id), UNIQUE KEY uk_account_username (username), UNIQUE KEY uk_account_email (email),
    CONSTRAINT fk_account_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

CREATE TABLE IF NOT EXISTS specialties (
    specialty_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL, description VARCHAR(1000), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (specialty_id), UNIQUE KEY uk_specialty_name (name)
);

CREATE TABLE IF NOT EXISTS doctors (
    doctor_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL, specialty_id BIGINT NOT NULL,
    full_name VARCHAR(100) NOT NULL, qualification VARCHAR(255), description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (doctor_id), UNIQUE KEY uk_doctor_account (account_id), KEY idx_doctor_specialty (specialty_id),
    CONSTRAINT fk_doctor_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_doctor_specialty FOREIGN KEY (specialty_id) REFERENCES specialties(specialty_id)
);

CREATE TABLE IF NOT EXISTS doctor_schedules (
    schedule_id BIGINT NOT NULL AUTO_INCREMENT, doctor_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (schedule_id), KEY idx_schedule_doctor_time (doctor_id, start_at, end_at),
    CONSTRAINT fk_schedule_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
);

CREATE TABLE IF NOT EXISTS schedule_slots (
    slot_id BIGINT NOT NULL AUTO_INCREMENT, schedule_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL, status VARCHAR(20) NOT NULL,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (slot_id), UNIQUE KEY uk_doctor_slot_start (doctor_id, start_at), KEY idx_slot_schedule (schedule_id),
    KEY idx_slot_available (doctor_id, status, start_at),
    CONSTRAINT fk_slot_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedules(schedule_id),
    CONSTRAINT fk_slot_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
);

CREATE TABLE IF NOT EXISTS appointments (
    appointment_id BIGINT NOT NULL AUTO_INCREMENT,
    patient_account_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL, slot_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(255), last_modified_by VARCHAR(255),
    created_date DATETIME(6), last_modified_date DATETIME(6),
    PRIMARY KEY (appointment_id), KEY idx_appointment_patient (patient_account_id),
    KEY idx_appointment_doctor (doctor_id), KEY idx_appointment_slot (slot_id),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id),
    CONSTRAINT fk_appointment_slot FOREIGN KEY (slot_id) REFERENCES schedule_slots(slot_id)
);

INSERT IGNORE INTO roles(role_name, created_by, created_date)
VALUES ('ADMIN', 'SYSTEM', CURRENT_TIMESTAMP),
       ('DOCTOR', 'SYSTEM', CURRENT_TIMESTAMP),
       ('PATIENT', 'SYSTEM', CURRENT_TIMESTAMP);
