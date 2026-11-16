package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentTestSupport {
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TenantTestSupport tenants;

    public Item item(Tenant tenant, String name, int price, boolean available) {
        Category category = categoryRepository.findByTenant_TenantIdAndCategoryName(tenant.getTenantId(), "Pay tests").orElseGet(() -> {
            Category made = new Category();
            made.setTenant(tenant);
            made.setMenu(tenants.menuOf(tenant));
            made.setCategoryName("Pay tests");
            return categoryRepository.save(made);
        });
        Item item = itemRepo.findByTenant_TenantIdAndItemName(tenant.getTenantId(), name).orElseGet(Item::new);
        item.setTenant(tenant);
        item.setItemName(name);
        item.setItemPrice(BigDecimal.valueOf(price));
        item.setCategoryId(category);
        item = itemRepo.save(item);
        item.setAvailable(available);
        return itemRepo.save(item);
    }

    // An order with one line, already in the given status.
    public Order order(Tenant tenant, OrderStatus status, Item item, int quantity) {
        Order order = new Order();
        order.setTenant(tenant);
        BigDecimal total = item.getItemPrice().multiply(BigDecimal.valueOf(quantity));
        order.setTotalAmount(total);
        order = orderRepo.save(order);
        OrderItem line = new OrderItem();
        line.setTenant(tenant);
        line.setOrder(order);
        line.setItem(item);
        line.setQuantity(quantity);
        line.setUnitPrice(item.getItemPrice());
        line.setTotalPrice(total);
        orderItemRepo.save(line);
        order.setOrderStatus(status);
        return orderRepo.save(order);
    }

    public Payment payment(Order order, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setTenant(order.getTenant());
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPlatelyChargeKobo(0L);
        payment.setTenantAmountKobo(order.getTotalAmount().movePointRight(2).longValue());
        payment.setSubaccountCode(TenantTestSupport.SUBACCOUNT);
        payment = paymentRepo.save(payment);
        payment.setReference("PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        payment.setStatus(status);
        return paymentRepo.saveAndFlush(payment);
    }

    public Payment paidOrder(Tenant tenant, OrderStatus orderStatus, Item item) {
        return payment(order(tenant, orderStatus, item, 1), PaymentStatus.SUCCESSFUL);
    }
}
