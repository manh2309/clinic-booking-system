package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7__reschedule_notifications extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        boolean h2 = "H2".equals(context.getConnection().getMetaData().getDatabaseProductName());
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE notifications DROP " + (h2 ? "CONSTRAINT" : "CHECK") + " chk_notification_type");
            statement.execute("ALTER TABLE notifications ADD CONSTRAINT chk_notification_type CHECK (type IN ("
                    + "'APPOINTMENT_BOOKED','APPOINTMENT_CONFIRMED','APPOINTMENT_CANCELED',"
                    + "'APPOINTMENT_COMPLETED','APPOINTMENT_EXPIRED','APPOINTMENT_NO_SHOW',"
                    + "'RESCHEDULE_PROPOSED','RESCHEDULE_ACCEPTED','RESCHEDULE_DECLINED','RESCHEDULE_WITHDRAWN'))");
        }
    }
}
