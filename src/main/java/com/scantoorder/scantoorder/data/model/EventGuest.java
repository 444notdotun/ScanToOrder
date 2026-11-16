package com.scantoorder.scantoorder.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Someone on the planner's list. Their code is their seat and their payment.
 */
@Entity
@Data
@Table(name = "event_guests", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "code"}))
public class EventGuest {
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
    private String phone;
    @JsonIgnore
    private String email;
    @Column(nullable = false, length = 6)
    private String code;

    // Null means as many plates as they like.
    private Integer plateLimit;
    private int platesUsed;

    // ASSIGNED seating only.
    @ManyToOne
    @JoinColumn(name = "assigned_seat_id")
    private Seat assignedSeat;

    // The seat they hold now (or held last); only an OCCUPIED one counts as "seated".
    @ManyToOne
    @JoinColumn(name = "session_seat_id")
    private SessionSeat sessionSeat;
    private LocalDateTime seatedAt;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
