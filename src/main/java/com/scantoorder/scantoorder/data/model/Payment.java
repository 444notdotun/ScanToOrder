package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String paymentId;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    @ManyToOne
    @JoinColumn(name = "orderId" , nullable = false)
    private Order orderId;
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;
    private String paymentReference;
    private String failureReason;
    private LocalDateTime paidAt;
    @CreatedBy
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist(){
        paymentStatus=PaymentStatus.PENDING;
        paymentReference = CodeGenerator.generate(CodePrefix.PAYMENT);
    }

}
