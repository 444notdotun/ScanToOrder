package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * One combination the planner offers, for example "Jollof rice, chicken and Coke". No price.
 */
@Entity
@Data
@Table(name = "event_plates")
public class EventPlate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String name;
    private String description;
    private boolean available = true;
}
