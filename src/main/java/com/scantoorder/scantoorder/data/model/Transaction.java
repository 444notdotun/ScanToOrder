package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String transactionId;
    @ManyToOne
    @JoinColumn(name = "orderId")
    private Order orderId;
    private TransactionMethod transactionMethod;
    private TransactionStatus transactionStatus;
    private BigDecimal amount;
    @CreatedBy
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;


}
