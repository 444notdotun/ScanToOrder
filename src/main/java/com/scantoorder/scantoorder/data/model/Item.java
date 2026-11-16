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
@jakarta.persistence.Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "item_name"}))
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String itemId;
    private String itemName;
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    private String itemDescription;
    private BigDecimal itemPrice;
    @ManyToOne
    @JoinColumn
    private Category categoryId;
    private boolean isAvailable;
    // A link to a photo of the dish. Uploading photos comes later; for now the manager pastes a link.
    @Column(length = 500)
    private String imageUrl;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.isAvailable=true;

    }


}
