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
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @Column(nullable = false, length = 6)
    private String joinCode;
    @Enumerated(EnumType.STRING)
    private DinningSessionStatus sessionStatus;
    @CreationTimestamp
    private String createdAt;
    private String completedAt;
    // True when staff closed the table or session, so the guest is told their visit ended.
    private boolean closedByStaff;

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
