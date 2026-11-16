package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;

@Entity
@Data
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String reviewId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
   @ManyToOne
    @JoinColumn(name = "sessionId")
    private DinningSession sessionId;
    private int rating;
    private String comment;
    @CreatedBy
    private String createdBy;


}
