package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderItemId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne
    @JoinColumn(name = "orderId")
    private Order order;
    @ManyToOne
    @JoinColumn(name = "itemId")
    private Item item;
    // An event order is one plate and has no menu item.
    @ManyToOne
    @JoinColumn(name = "plateId")
    private EventPlate plate;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String SpecialInstructions;
    // True for plates the kitchen could not make and a waiter swapped out.
    private boolean swappedOut;

}
