package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ReadyTimeTest {
    @Autowired
    private ReadyTimeService readyTimes;
    @Autowired
    private MenuService menuService;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categories;

    private Tenant tenant;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        tenant = tenants.freshActiveTenant();
        now = LocalDateTime.now();
    }

    private void delivered(Item item, long minutes, LocalDateTime deliveredAt) {
        Order order = pay.order(tenant, OrderStatus.DELIVERED, item, 1);
        order.setPaidAt(deliveredAt.minusMinutes(minutes));
        order.setDeliveredAt(deliveredAt);
        orderRepo.save(order);
    }

    @Test
    void testThatADishWithFiveDeliveredOrdersGetsTheMedianOfThemInMinutes() {
        Item item = pay.item(tenant, "Median plate", 2000, true);
        for (long minutes : new long[]{10, 12, 14, 16, 40}) delivered(item, minutes, now.minusDays(2));

        assertEquals(14, readyTimes.byItemName(tenant.getTenantId(), now).get("Median plate"));
    }

    @Test
    void testThatAnEvenNumberOfOrdersUsesTheMiddleTwo() {
        Item item = pay.item(tenant, "Even plate", 2000, true);
        for (long minutes : new long[]{10, 12, 16, 20, 30, 50}) delivered(item, minutes, now.minusDays(1));

        assertEquals(18, readyTimes.byItemName(tenant.getTenantId(), now).get("Even plate"));
    }

    @Test
    void testThatADishWithFewerThanFiveOrdersGetsNoFigureAtAll() {
        Item item = pay.item(tenant, "Rare plate", 2000, true);
        for (long minutes : new long[]{10, 12, 14, 16}) delivered(item, minutes, now.minusDays(2));

        assertNull(readyTimes.byItemName(tenant.getTenantId(), now).get("Rare plate"));
    }

    @Test
    void testThatOrdersOlderThanThirtyDaysAndOrdersNotDeliveredDoNotCount() {
        Item item = pay.item(tenant, "Old plate", 2000, true);
        for (long minutes : new long[]{10, 12, 14}) delivered(item, minutes, now.minusDays(2));
        for (long minutes : new long[]{10, 12}) delivered(item, minutes, now.minusDays(31));
        pay.order(tenant, OrderStatus.PREPARING, item, 1);
        pay.order(tenant, OrderStatus.READY, item, 1);

        assertNull(readyTimes.byItemName(tenant.getTenantId(), now).get("Old plate"));
    }

    @Test
    void testThatAnotherBusinessesOrdersNeverCountForThisOne() {
        Item mine = pay.item(tenant, "Shared name plate", 2000, true);
        Tenant other = tenants.other();
        Item theirs = pay.item(other, "Shared name plate", 2000, true);
        for (long minutes : new long[]{10, 12, 14, 16, 18}) {
            Order order = pay.order(other, OrderStatus.DELIVERED, theirs, 1);
            order.setPaidAt(now.minusDays(1).minusMinutes(minutes));
            order.setDeliveredAt(now.minusDays(1));
            orderRepo.save(order);
        }
        delivered(mine, 10, now.minusDays(1));

        assertNull(readyTimes.byItemName(tenant.getTenantId(), now).get("Shared name plate"));
        assertEquals(14, readyTimes.byItemName(other.getTenantId(), now).get("Shared name plate"));
    }

    @Test
    void testThatTheGuestMenuCarriesTheFigureOnlyForADishThatHasEnoughHistoryAndThePhotoOnlyWhenThereIsOne() {
        Category category = new Category();
        category.setTenant(tenant);
        category.setMenu(tenants.menuOf(tenant));
        category.setCategoryName("Ready times");
        category = categories.save(category);
        Item busy = pay.item(tenant, "Busy plate", 2000, true);
        busy.setCategoryId(category);
        busy.setImageUrl("https://example.com/busy.jpg");
        itemRepo.save(busy);
        Item quiet = pay.item(tenant, "Quiet plate", 2000, true);
        quiet.setCategoryId(category);
        itemRepo.save(quiet);
        for (long minutes : new long[]{9, 11, 13, 15, 17}) delivered(busy, minutes, LocalDateTime.now().minusDays(1));
        menuService.clearCache(tenant.getTenantId());

        MenuResponse menu = menuService.generateMenu(tenant.getTenantId());
        Map<String, com.scantoorder.scantoorder.dtos.respond.ItemResponse> byName = new java.util.HashMap<>();
        menu.getCategoryAndItemResponse().forEach(section -> section.getItemResponse().forEach(item -> byName.put(item.getItemName(), item)));

        assertEquals(13, byName.get("Busy plate").getReadyInMinutes());
        assertEquals("https://example.com/busy.jpg", byName.get("Busy plate").getImageUrl());
        assertNull(byName.get("Quiet plate").getReadyInMinutes());
        assertNull(byName.get("Quiet plate").getImageUrl());
    }
}
