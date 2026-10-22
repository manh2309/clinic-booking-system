package com.xxxx.clinicbookingsystem;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AppointmentLifecycleMigrationTest {
    @Test
    void extendsNotificationTypesWithoutChangingOldNotifications() {
        String url = "jdbc:h2:mem:notification_migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var previous = Flyway.configure().dataSource(url, "sa", "").target("3").load();
        previous.migrate();
        var jdbc = new JdbcTemplate(previous.getConfiguration().getDataSource());
        jdbc.update("INSERT INTO accounts(account_id,username,password,role_id) "
                + "SELECT 21,'notification_patient','unused',role_id FROM roles WHERE role_name='PATIENT'");
        jdbc.update("INSERT INTO notifications(notification_id,recipient_account_id,type,title,message,is_read) "
                + "VALUES (21,21,'APPOINTMENT_BOOKED','Existing title','Existing message',FALSE)");
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE notification_id=21", String.class))
                .isEqualTo("Existing message");
        jdbc.update("UPDATE notifications SET type='APPOINTMENT_EXPIRED' WHERE notification_id=21");
        jdbc.update("UPDATE notifications SET type='APPOINTMENT_NO_SHOW' WHERE notification_id=21");
        assertThatThrownBy(() -> jdbc.update("UPDATE notifications SET type='UNKNOWN' WHERE notification_id=21"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upgradesLegacyCancelWithoutDroppingAppointmentHistory() {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var previous = Flyway.configure().dataSource(url, "sa", "").target("2").load();
        previous.migrate();
        var jdbc = new JdbcTemplate(previous.getConfiguration().getDataSource());
        jdbc.update("INSERT INTO accounts(account_id,username,password,role_id) "
                + "SELECT 11,'migration_doctor','unused',role_id FROM roles WHERE role_name='DOCTOR'");
        jdbc.update("INSERT INTO accounts(account_id,username,password,role_id) "
                + "SELECT 12,'migration_patient','unused',role_id FROM roles WHERE role_name='PATIENT'");
        jdbc.update("INSERT INTO specialties(specialty_id,name) VALUES (11,'Migration specialty')");
        jdbc.update("INSERT INTO doctors(doctor_id,account_id,specialty_id,full_name) VALUES (11,11,11,'Migration doctor')");
        jdbc.update("INSERT INTO doctor_schedules(schedule_id,doctor_id,start_at,end_at) "
                + "VALUES (11,11,'2030-10-15 09:00:00','2030-10-15 09:30:00')");
        jdbc.update("INSERT INTO schedule_slots(slot_id,schedule_id,doctor_id,start_at,end_at,status) "
                + "VALUES (11,11,11,'2030-10-15 09:00:00','2030-10-15 09:30:00','BOOKED')");
        jdbc.update("INSERT INTO appointments(appointment_id,patient_account_id,doctor_id,slot_id,status) "
                + "VALUES (11,12,11,11,'CANCEL')");

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE appointment_id=11", String.class))
                .isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class)).isEqualTo(1);
        // Đổi tên trạng thái không đồng nghĩa được mở slot: lịch khác có thể đang dùng slot đó.
        assertThat(jdbc.queryForObject("SELECT status FROM schedule_slots WHERE slot_id=11", String.class)).isEqualTo("BOOKED");
        assertThatThrownBy(() -> jdbc.update("UPDATE appointments SET status='CANCEL' WHERE appointment_id=11"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
