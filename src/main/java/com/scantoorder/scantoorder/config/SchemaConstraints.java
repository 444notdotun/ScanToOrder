package com.scantoorder.scantoorder.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Constraints Hibernate annotations cannot express. Runs after the schema is built.
 */
@Component
@Order(1)
public class SchemaConstraints implements ApplicationRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        entityManager.createNativeQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_session_seat_one_active_per_seat "
                        + "ON session_seat (seat_id) WHERE status IN ('HELD', 'OCCUPIED')"
        ).executeUpdate();
        // Restaurant tables are numbered per business, event tables per event.
        entityManager.createNativeQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_table_number_per_business "
                        + "ON restaurant_table (tenant_id, display_number) WHERE event_id IS NULL"
        ).executeUpdate();
        entityManager.createNativeQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_table_number_per_event "
                        + "ON restaurant_table (event_id, display_number) WHERE event_id IS NOT NULL"
        ).executeUpdate();
    }
}
