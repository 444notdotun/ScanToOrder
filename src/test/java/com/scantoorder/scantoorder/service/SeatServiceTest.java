package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SeatTakenException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeatServiceTest {

    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private SeatLedger seatLedger;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private TableRepo tableRepo;

    private RestaurantTable table;
    private List<Seat> seats;

    @BeforeEach
    void setUp() {
        support.clearFloor();
        table = support.newTable(2);
        seats = support.seatsOf(table);
    }

    @Test
    void testThatSeatStatusCanBeSeen() {
        assertEquals(2, seatService.viewAllSeatStatus(support.tenantId()).size());
    }

    @Test
    void testThatAVacantSeatCanBeClaimedAndStartsASession() {
        SeatClaimedResponse response = seatService.claimSeat(support.claimRequest(seats.get(0), "adedotun"), null);

        assertEquals("Seat claimed successfully", response.getMessage());
        assertNotNull(response.getToken());
        assertEquals(6, response.getJoinCode().length());
        assertEquals(1, response.getPosition());
        assertEquals(SeatStatus.OCCUPIED, support.reload(seats.get(0)).getStatus());

        SessionSeat membership = support.membershipOf(seats.get(0).getSeatId());
        assertEquals(SessionSeatStatus.OCCUPIED, membership.getStatus());
        assertEquals("adedotun", membership.getGuestName());
        assertEquals("08149048149", membership.getGuestPhone());
        assertNull(membership.getGuestEmail());
        assertTrue(membership.isHasDevice());
        assertEquals(response.getSessionId(), membership.getSession().getSessionId());
        assertEquals(response.getJoinCode(), membership.getSession().getJoinCode());
    }

    @Test
    void testThatAnOccupiedSeatCannotBeClaimed() {
        support.claim(seats.get(0), "first");
        SeatTakenException exception = assertThrows(SeatTakenException.class,
                () -> seatService.claimSeat(support.claimRequest(seats.get(0), "second"), null));
        assertTrue(exception.getMessage().contains("just taken"));
    }

    @Test
    void testThatAFullTableSaysItIsFull() {
        support.claim(seats.get(0), "first");
        support.claim(seats.get(1), "second");
        SeatTakenException exception = assertThrows(SeatTakenException.class,
                () -> seatService.claimSeat(support.claimRequest(seats.get(0), "third"), null));
        assertTrue(exception.getMessage().contains("table is full"));
    }

    @Test
    void testThatAHeldSeatCannotBeClaimedWithoutTheJoinCode() {
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        DinningSession session = dinningSessionRepo.findById(host.getSessionId()).orElseThrow();
        seatLedger.hold(session, support.reload(seats.get(1)));

        SeatTakenException exception = assertThrows(SeatTakenException.class,
                () -> seatService.claimSeat(support.claimRequest(seats.get(1), "stranger"), null));
        assertTrue(exception.getMessage().contains("join code"));
    }

    @Test
    void testThatASeatWhoseHoldHasRunOutCanBeClaimed() {
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        DinningSession session = dinningSessionRepo.findById(host.getSessionId()).orElseThrow();
        SessionSeat hold = seatLedger.hold(session, support.reload(seats.get(1)));
        support.expireHold(seats.get(1).getSeatId());

        SeatClaimedResponse response = seatService.claimSeat(support.claimRequest(seats.get(1), "stranger"), null);

        assertNotEquals(host.getSessionId(), response.getSessionId());
        assertEquals(SessionSeatStatus.RELEASED, sessionSeatRepo.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(SeatStatus.OCCUPIED, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatASeatAtASwitchedOffTableCannotBeClaimed() {
        table.setActive(false);
        tableRepo.save(table);
        assertThrows(TableNotFoundException.class,
                () -> seatService.claimSeat(support.claimRequest(seats.get(0), "guest"), null));
    }

    @Test
    void testThatTheSeatMustBelongToTheScannedTable() {
        ClaimSeatRequest request = support.claimRequest(seats.get(0), "guest");
        request.setTableId(support.newTable(1).getTableNumber());
        assertThrows(TableNotFoundException.class, () -> seatService.claimSeat(request, null));
    }

    @Test
    void testThatAnUnknownSeatCannotBeClaimed() {
        ClaimSeatRequest request = support.claimRequest(seats.get(0), "guest");
        request.setSeatId("no-such-seat");
        assertThrows(SeatNotFoundException.class, () -> seatService.claimSeat(request, null));
    }

    @Test
    void testThatASeatedGuestWhoScansTheirOwnTableIsReturnedToTheirSeat() {
        CustomerPrincipal guest = support.claim(seats.get(0), "guest");

        SeatClaimedResponse response = seatService.claimSeat(support.claimRequest(seats.get(1), "guest"), guest);

        assertNull(response.getToken());
        assertEquals(seats.get(0).getSeatId(), response.getSeatId());
        assertEquals(guest.getSessionId(), response.getSessionId());
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatASeatedGuestCannotTakeASeatAtAnotherTable() {
        CustomerPrincipal guest = support.claim(seats.get(0), "guest");
        Seat elsewhere = support.seatsOf(support.newTable(1)).get(0);

        assertThrows(SeatStatusException.class,
                () -> seatService.claimSeat(support.claimRequest(elsewhere, "guest"), guest));
        assertEquals(SeatStatus.VACANT, support.reload(elsewhere).getStatus());
    }

    @Test
    void testThatOnlyOneOfTwoPhonesClaimingTheSameSeatWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                seatService.claimSeat(support.claimRequest(seats.get(0), "racer"), null);
                return true;
            } catch (SeatTakenException e) {
                return false;
            }
        };
        Future<Boolean> first = pool.submit(attempt);
        Future<Boolean> second = pool.submit(attempt);
        start.countDown();
        boolean firstWon = first.get();
        boolean secondWon = second.get();
        pool.shutdown();

        assertTrue(firstWon ^ secondWon);
        assertEquals(1, sessionSeatRepo.findAll().size());
        assertEquals(1, dinningSessionRepo.findAll().size());
    }

    @Test
    void testThatStaffCannotSetASeatHeldOrOccupiedByHand() {
        UpdateSeatRequest request = new UpdateSeatRequest();
        request.setSeatId(seats.get(0).getSeatId());
        request.setNewState("OCCUPIED");
        assertThrows(SeatStatusException.class, () -> seatService.updateSeat(support.tenantId(), request));
        request.setNewState("HELD");
        assertThrows(SeatStatusException.class, () -> seatService.updateSeat(support.tenantId(), request));
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(0)).getStatus());
    }

    @Test
    void testThatStaffFreeingOneSeatLeavesTheRestOfTheSessionAlone() {
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        DinningSession session = dinningSessionRepo.findById(host.getSessionId()).orElseThrow();
        seatLedger.hold(session, support.reload(seats.get(1)));

        seatService.releaseSeat(support.tenantId(), seats.get(1).getSeatId());

        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
        assertEquals(SeatStatus.OCCUPIED, support.reload(seats.get(0)).getStatus());
        assertEquals(DinningSessionStatus.ACTIVE, dinningSessionRepo.findById(host.getSessionId()).orElseThrow().getSessionStatus());
    }

    @Test
    void testThatStaffFreeingTheLastOccupiedSeatClosesTheSessionAndDropsItsHolds() {
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        DinningSession session = dinningSessionRepo.findById(host.getSessionId()).orElseThrow();
        seatLedger.hold(session, support.reload(seats.get(1)));

        UpdateSeatRequest request = new UpdateSeatRequest();
        request.setSeatId(seats.get(0).getSeatId());
        request.setNewState("VACANT");
        seatService.updateSeat(support.tenantId(), request);

        assertEquals(SeatStatus.VACANT, support.reload(seats.get(0)).getStatus());
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
        assertEquals(DinningSessionStatus.CLOSED, dinningSessionRepo.findById(host.getSessionId()).orElseThrow().getSessionStatus());
    }

    @Test
    void testThatFreeingAnAlreadyFreeSeatIsHarmless() {
        assertEquals("Seat is already free", seatService.releaseSeat(support.tenantId(), seats.get(0).getSeatId()).getMessage());
    }
}
