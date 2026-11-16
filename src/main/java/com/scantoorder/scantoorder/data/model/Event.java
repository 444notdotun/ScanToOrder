package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One occasion run by an event planner: a wedding, a launch. Guests come with codes the planner paid for.
 */
@Entity
@Data
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;
    private String venue;
    @Column(nullable = false)
    private LocalDateTime startsAt;
    @Column(nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSeatingMode seatingMode;
    // Plates each guest may have; null means as many as they like.
    private Integer platesPerGuest;
    private int expectedGuests;
    // How many guests the planner has paid for. The guest list can never be longer.
    private int paidGuests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = EventStatus.DRAFT;
    }
}
