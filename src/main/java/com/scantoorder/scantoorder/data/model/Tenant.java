package com.scantoorder.scantoorder.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tenantId;
    private String name;
    @Column(nullable = false, unique = true)
    private String slug;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;
    private boolean openForOrders;
    private boolean holdsEnabled;
    private String ownerName;
    @Column(nullable = false, unique = true)
    private String email;
    private String phone;
    private String bankCode;
    private String bankName;
    @JsonIgnore
    private String accountNumber;
    private String accountName;
    private String declineReason;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = TenantStatus.PENDING_APPROVAL;
    }

    // A guest can start a new session only while the business is live and has switched itself on.
    public boolean isTakingOrders() {
        return status == TenantStatus.ACTIVE && openForOrders;
    }
}
