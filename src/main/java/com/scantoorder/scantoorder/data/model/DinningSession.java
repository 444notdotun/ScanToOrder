package com.scantoorder.scantoorder.data.model;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class DinningSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String sessionId;
    @ManyToOne
    @JoinColumn(name = "tableId")
    private RestaurantTable tableId;
    private String customerName;
    @Nullable
    private String customerEmail;
    @Column(nullable = false)
    private String customerPhone;
    @Enumerated(EnumType.STRING)
    private DinningSessionStatus sessionStatus;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dinning_session_seats", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "seat_id")
    private List<String> claimedSeatIds = new ArrayList<>();
    
    @CreationTimestamp
    private String createdAt;
    private String completedAt;

    public DinningSession(){
        this.sessionStatus=DinningSessionStatus.ACTIVE;
    }
    
    @PrePersist
    public void prePersist(){
            this.sessionStatus = DinningSessionStatus.ACTIVE;
    }
}
