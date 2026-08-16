package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;

@Data
@Entity

public class Worker {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String workerId;
    private String username;
    private String password;
    private String fullName;
    @Enumerated(EnumType.STRING)
    private WorkerRole role;
    private boolean isActive;
    @CreatedBy
    private String createdAt;
    @UpdateTimestamp
    private String updatedAt;


    @PrePersist
    public void prePersist(){
        isActive=true;
    }
}