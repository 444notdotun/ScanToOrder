package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;

import java.time.LocalDateTime;

@Entity
@Data
public class ServiceCall {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String serviceCallId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne
    @JoinColumn(name = "sessionId")
    private DinningSession sessionId;
    private String serviceDescription;
    @Enumerated(EnumType.STRING)
    private ServiceStatus serviceStatus;
    @CreatedBy
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    @ManyToOne
    @JoinColumn(name = "workerId")
    private Worker assignedWorker;

    @PrePersist
    public void persist(){
        this.serviceStatus=ServiceStatus.UNASSIGNED;
    }


}
