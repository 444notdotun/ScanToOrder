package com.scantoorder.scantoorder.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Money the restaurant owes a guest. Plately never moves it: the restaurant pays the guest back and
 * records that here. One refund per payment.
 */
@Entity
@Data
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(columnNames = "payment_id"))
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @OneToOne(optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // Everything the guest paid for that payment. Plately's charge stays with Plately.
    @Column(nullable = false)
    private long amountKobo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundReason reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;
    @Enumerated(EnumType.STRING)
    private RefundClosedBy closedBy;
    private LocalDateTime guestConfirmedAt;

    private String guestBankCode;
    private String guestBankName;
    @JsonIgnore
    private String guestAccountNumber;
    private String guestAccountName;

    // Where the guest wants to be told about this refund. Never logged.
    private String guestEmail;

    @Enumerated(EnumType.STRING)
    private RefundMethod method;
    private String transferReference;
    private LocalDateTime settledAt;
    private String settledBy;
    private LocalDateTime guestReportedMissingAt;

    @JsonIgnore
    @Column(nullable = false, unique = true)
    private String guestToken;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = RefundStatus.DUE;
    }

    // Open means the restaurant still has something to do: pay it, or put right a payment the guest never got.
    public boolean isOpen() {
        return status == RefundStatus.DUE || (status == RefundStatus.SETTLED && guestReportedMissingAt != null);
    }
}
