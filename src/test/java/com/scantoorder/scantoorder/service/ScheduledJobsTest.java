package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ScheduledJobsTest {
    @Autowired
    private ScheduledJobs scheduledJobs;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private SeatingTestSupport support;

    private List<Seat> seats;
    private CustomerPrincipal host;

    @BeforeEach
    void setUp() {
        support.clearFloor();
        seats = support.seatsOf(support.newTable(4));
        host = support.claim(seats.get(0), "host");
    }

    @Test
    void testThatTheHoldJobFreesOnlyHoldsWhoseTimeHasPassed() {
        diningSessionService.holdSeats(host, List.of(seats.get(1).getSeatId(), seats.get(2).getSeatId()));
        support.expireHold(seats.get(1).getSeatId());

        scheduledJobs.expireHolds();

        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
        assertEquals(SeatStatus.HELD, support.reload(seats.get(2)).getStatus());
        assertEquals(SeatStatus.OCCUPIED, support.reload(seats.get(0)).getStatus());
        assertEquals(2, diningSessionService.getMySession(host).getSeats().size());
    }

    @Test
    void testThatTheEndOfDayJobClosesEverySessionAndFreesItsSeats() {
        diningSessionService.holdSeats(host, List.of(seats.get(1).getSeatId()));
        CustomerPrincipal stranger = support.claim(seats.get(3), "stranger");

        scheduledJobs.closeSessionsAtEndOfDay();

        for (Seat seat : seats) {
            assertEquals(SeatStatus.VACANT, support.reload(seat).getStatus());
        }
        assertEquals(DinningSessionStatus.CLOSED, dinningSessionRepo.findById(host.getSessionId()).orElseThrow().getSessionStatus());
        assertEquals(DinningSessionStatus.CLOSED, dinningSessionRepo.findById(stranger.getSessionId()).orElseThrow().getSessionStatus());
        assertEquals(0, diningSessionService.getActiveSessionsCount());
    }
}
