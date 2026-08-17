package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String itemId;
    @Column(unique = true)
    private String itemName;
    private String itemDescription;
    private BigDecimal itemPrice;
    @ManyToOne
    @JoinColumn
    private Category categoryId;
    private boolean isAvailable;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.isAvailable=true;

    }


}
