package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import com.scantoorder.scantoorder.service.Interface.MenuService;
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
    private CategoryRepository categories;
    @Autowired
    private MenuService menuService;
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
    void testThatSwitchingAnItemOffHidesItFromGuestsAndSwitchingItOnBringsItBack() {
        Item item = pay.item(tenant, "Hidden when off", 2000, true);
        item.setCategoryId(categories.save(category()));
        itemRepo.save(item);
        menuService.clearCache(tenant.getTenantId());
        assertTrue(menuText().contains("Hidden when off"));

        itemService.toggleItem(item.getItemId());
        assertFalse(menuText().contains("Hidden when off"));
        assertTrue(itemRepo.findById(item.getItemId()).isPresent());

        itemService.toggleItem(item.getItemId());
        assertTrue(menuText().contains("Hidden when off"));
    }

    private Category category() {
        Category category = new Category();
        category.setTenant(tenant);
        category.setMenu(tenants.menuOf(tenant));
        category.setCategoryName("Hide and show");
        return category;
    }

    private String menuText() {
        return menuService.generateMenu(tenant.getTenantId()).toString();
    }

    @Test
    void testThatAnItemNobodyHasOrderedCanBeDeleted() {
        Item item = pay.item(tenant, "Never ordered", 2000, true);

        itemService.deleteItem(item.getItemId());

        assertTrue(itemRepo.findById(item.getItemId()).isEmpty());
    }
}
