-- Chuẩn hóa dữ liệu cũ trước khi giới hạn các giá trị status.
UPDATE appointments SET status = 'CANCELED' WHERE status = 'CANCEL';

ALTER TABLE appointments ADD COLUMN status_reason VARCHAR(500);
ALTER TABLE appointments ADD CONSTRAINT chk_appointment_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELED', 'EXPIRED', 'NO_SHOW'));
