package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class SessionSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private DinningSession session;
    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionSeatStatus status;
    private String guestName;
    private String guestPhone;
    private String guestEmail;
    private boolean hasDevice;
    private LocalDateTime heldUntil;
    private LocalDateTime claimedAt;
    private LocalDateTime releasedAt;
}
