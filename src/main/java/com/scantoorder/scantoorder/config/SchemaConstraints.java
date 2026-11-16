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
        dropOldTableNumberConstraint();
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

    // A database kept with DDL_AUTO=update still has the old rule that a business's table numbers are unique,
    // which would refuse an event's Table 1. Its name is made up by Hibernate, so it is looked up, never guessed.
    private void dropOldTableNumberConstraint() {
        @SuppressWarnings("unchecked")
        java.util.List<String> names = entityManager.createNativeQuery(
                "SELECT c.conname FROM pg_constraint c "
                        + "WHERE c.conrelid = to_regclass('public.restaurant_table') AND c.contype = 'u' "
                        + "AND (SELECT array_agg(a.attname::text ORDER BY a.attname::text) FROM pg_attribute a "
                        + "WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)) = ARRAY['display_number', 'tenant_id']"
        ).getResultList();
        for (String name : names) {
            entityManager.createNativeQuery("ALTER TABLE public.restaurant_table DROP CONSTRAINT IF EXISTS \"" + name.replace("\"", "\"\"") + "\"")
                    .executeUpdate();
        }
    }
}
