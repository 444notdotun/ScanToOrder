package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ItemDeleteTest {
    @Autowired
    private ItemService itemService;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport pay;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenants.signInAsManager();
        tenant = tenants.main();
    }

    @AfterEach
    void tearDown() {
        tenants.signOut();
    }

    @Test
    void testThatAnItemThatWasOrderedCanBeSwitchedOffButNotDeleted() {
        Item item = pay.item(tenant, "Ordered before", 2000, true);
        pay.order(tenant, OrderStatus.PAID, item, 1);

        ConflictException refused = assertThrows(ConflictException.class, () -> itemService.deleteItem(item.getItemId()));

        assertEquals("It's been ordered before, so it can only be switched off.", refused.getMessage());
        assertTrue(itemRepo.findById(item.getItemId()).isPresent());
        assertFalse(itemService.toggleItem(item.getItemId()).isAvailable());
    }

    @Test
    void testThatAnItemNobodyHasOrderedCanBeDeleted() {
        Item item = pay.item(tenant, "Never ordered", 2000, true);

        itemService.deleteItem(item.getItemId());

        assertTrue(itemRepo.findById(item.getItemId()).isEmpty());
    }
}
