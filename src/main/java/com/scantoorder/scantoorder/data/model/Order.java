package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Data
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderId;
    @ManyToOne
    @JoinColumn(name = "table_id")
    private RestaurantTable table;
    @ManyToOne
    @JoinColumn(name = "seat_id")
    private Seat seat;
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;
    private BigDecimal totalAmount;
    private String description;
    private String OrderNumber;
    @ManyToOne
    @JoinColumn(name = "session_id")
    private DinningSession dinningSession;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist(){
        orderStatus=OrderStatus.PENDING_PAYMENT;
        OrderNumber= CodeGenerator.generate(CodePrefix.ORDER);
    }



}
