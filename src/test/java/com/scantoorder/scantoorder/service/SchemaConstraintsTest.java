package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.SchemaConstraints;
import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventSeatingMode;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A database kept with DDL_AUTO=update has the old rule that table numbers are unique per business.
 * The DDL runs in the test's own transaction, so it is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class SchemaConstraintsTest {
    @Autowired
    private SchemaConstraints schemaConstraints;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private EventTestSupport support;
    @PersistenceContext
    private EntityManager entityManager;

    private long oldConstraints() {
        return ((Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM pg_constraint c WHERE c.conrelid = to_regclass('public.restaurant_table') AND c.contype = 'u'")
                .getSingleResult()).longValue();
    }

    private RestaurantTable table(Tenant tenant, Event event, int number) {
        RestaurantTable table = new RestaurantTable();
        table.setTenant(tenant);
        table.setEvent(event);
        table.setCapacity(2);
        table.setDisplayNumber(number);
        return tableRepo.saveAndFlush(table);
    }

    @Test
    void testThatTheOldPerBusinessNumberRuleIsDroppedByItsRealNameAndEventTablesCanStartAtOne() {
        Tenant planner = support.planner();
        Event first = support.activeEvent(planner, EventSeatingMode.FREE, 1, 1);
        Event second = support.activeEvent(planner, EventSeatingMode.FREE, 1, 1);
        // What an updated database still carries from before: a unique constraint on (tenant_id, display_number).
        entityManager.createNativeQuery("ALTER TABLE public.restaurant_table ADD CONSTRAINT uk_old_table_number_rule UNIQUE (tenant_id, display_number)").executeUpdate();
        long before = oldConstraints();
        assertTrue(before >= 1);

        schemaConstraints.run(null);

        assertEquals(before - 1, oldConstraints());
        assertEquals(0, ((Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'uk_old_table_number_rule'").getSingleResult()).longValue());
        table(planner, first, 1);
        table(planner, second, 1);
        // The new rules still hold: one number per event, and one per business for restaurant tables.
        assertThrows(DataIntegrityViolationException.class, () -> table(planner, first, 1));
    }

    @Test
    void testThatRunningItAgainWhenThereIsNothingToDropChangesNothing() {
        long before = oldConstraints();
        assertDoesNotThrow(() -> schemaConstraints.run(null));
        assertDoesNotThrow(() -> schemaConstraints.run(null));
        assertEquals(before, oldConstraints());
    }
}
