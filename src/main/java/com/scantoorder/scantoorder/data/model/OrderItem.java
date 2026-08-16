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
    @ManyToOne
    @JoinColumn(name = "orderId")
    private Order orderId;
    @ManyToOne
    @JoinColumn(name = "itemId")
    private Item itemId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String SpecialInstructions;

}
