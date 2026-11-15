package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Data
public class DinningSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String sessionId;
    @ManyToOne
    @JoinColumn(name = "tableId")
    private RestaurantTable tableId;
    @Column(nullable = false, length = 6)
    private String joinCode;
    @Enumerated(EnumType.STRING)
    private DinningSessionStatus sessionStatus;
    @CreationTimestamp
    private String createdAt;
    private String completedAt;

    public DinningSession(){
        this.sessionStatus=DinningSessionStatus.ACTIVE;
    }
    @PrePersist
    public void prePersist(){
        this.sessionStatus = DinningSessionStatus.ACTIVE;
        if (this.joinCode == null) {
            this.joinCode = CodeGenerator.generateJoinCode();
        }
    }
}
