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
   @ManyToOne
    @JoinColumn(name = "sessionId")
    private DinningSession sessionId;
    private int rating;
    private String comment;
    @CreatedBy
    private String createdBy;


}
