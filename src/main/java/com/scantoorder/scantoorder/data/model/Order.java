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
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
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
    @Enumerated(EnumType.STRING)
    private UnavailableReason unavailableReason;
    // Settled at the table: records a swap's price difference. No money moves in the app.
    private String note;
    private LocalDateTime swappedAt;
    // The timeline. Steps only move forward: PAID, PREPARING, READY, DELIVERED.
    private LocalDateTime paidAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime deliveredAt;
    // Who took each step first, for example "Chef Bisi", so a second tap can be told.
    private String preparingBy;
    private String readyBy;
    private String deliveredBy;
    // Set for an event order: it was placed with a guest's code and nothing was paid.
    // Lazy: an order should not drag a whole event, its guest and their seat into every query.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_guest_id")
    private EventGuest eventGuest;
    private String OrderNumber;
    @ManyToOne
    @JoinColumn(name = "session_id")
    private DinningSession dinningSession;
    @ManyToOne
    @JoinColumn(name = "session_seat_id")
    private SessionSeat sessionSeat;
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
