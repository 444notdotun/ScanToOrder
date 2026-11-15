package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.respond.SessionSeatView;
import com.scantoorder.scantoorder.dtos.respond.SessionViewResponse;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SeatOwnershipException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SeatTakenException;
import com.scantoorder.scantoorder.exception.SessionEndedException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DinningSessionsTest {
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatingTestSupport support;

    private RestaurantTable table;
    private List<Seat> seats;
    private CustomerPrincipal host;

    @BeforeEach
    void setup() {
        support.clearFloor();
        table = support.newTable(4);
        seats = support.seatsOf(table);
        host = support.claim(seats.get(0), "host");
    }

    @AfterEach
    void switchHoldsBackOn() {
        setHoldsEnabled(true);
    }

    private void setHoldsEnabled(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(diningSessionService);
        ReflectionTestUtils.setField(target, "holdsEnabled", enabled);
    }

    private String id(int index) {
        return seats.get(index).getSeatId();
    }

    private ConfirmGuestRequest guest(int index, String name) {
        ConfirmGuestRequest request = new ConfirmGuestRequest();
        request.setSeatId(id(index));
        request.setGuestName(name);
        return request;
    }

    @Test
    void testThatASeatedGuestCanHoldSeveralSeatsForTenMinutes() {
        SessionViewResponse view = diningSessionService.holdSeats(host, List.of(id(1), id(2)));

        assertEquals(3, view.getSeats().size());
        assertEquals(SeatStatus.HELD, support.reload(seats.get(1)).getStatus());
        assertEquals(SeatStatus.HELD, support.reload(seats.get(2)).getStatus());
        SessionSeat hold = support.membershipOf(id(1));
        assertEquals(SessionSeatStatus.HELD, hold.getStatus());
        assertEquals(host.getSessionId(), hold.getSession().getSessionId());
        assertTrue(hold.getHeldUntil().isAfter(LocalDateTime.now().plusMinutes(9)));
        assertTrue(hold.getHeldUntil().isBefore(LocalDateTime.now().plusMinutes(11)));
    }

    @Test
    void testThatHoldingIsAllOrNothing() {
        support.claim(seats.get(2), "stranger");

        assertThrows(SeatTakenException.class, () -> diningSessionService.holdSeats(host, List.of(id(1), id(2))));

        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
        assertEquals(1, diningSessionService.getMySession(host).getSeats().size());
    }

    @Test
    void testThatASeatAtAnotherTableCannotBeHeld() {
        Seat elsewhere = support.seatsOf(support.newTable(2)).get(0);
        assertThrows(SeatStatusException.class, () -> diningSessionService.holdSeats(host, List.of(id(1), elsewhere.getSeatId())));
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
        assertEquals(SeatStatus.VACANT, support.reload(elsewhere).getStatus());
    }

    @Test
    void testThatAnUnknownSeatCannotBeHeld() {
        assertThrows(SeatNotFoundException.class, () -> diningSessionService.holdSeats(host, List.of("no-such-seat")));
    }

    @Test
    void testThatASeatAlreadyHeldByTheSessionCannotBeHeldAgainUntilItExpires() {
        diningSessionService.holdSeats(host, List.of(id(1)));
        assertThrows(SeatStatusException.class, () -> diningSessionService.holdSeats(host, List.of(id(1))));

        support.expireHold(id(1));
        diningSessionService.holdSeats(host, List.of(id(1)));
        assertTrue(support.membershipOf(id(1)).getHeldUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void testThatAGuestWhoIsNotSeatedCannotHold() {
        seatService.releaseSeat(id(0));
        assertThrows(SessionEndedException.class, () -> diningSessionService.holdSeats(host, List.of(id(1))));
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatHoldsAreRejectedWhenSwitchedOff() {
        setHoldsEnabled(false);
        assertThrows(SeatStatusException.class, () -> diningSessionService.holdSeats(host, List.of(id(1))));
        assertFalse(diningSessionService.getMySession(host).isHoldsEnabled());
    }

    @Test
    void testThatAnySeatedMemberCanReleaseAHoldEarly() {
        diningSessionService.holdSeats(host, List.of(id(1), id(2)));
        support.membershipOf(id(1));
        CustomerPrincipal friend = joinAs(1, "friend");

        diningSessionService.releaseHold(friend, id(2));

        assertEquals(SeatStatus.VACANT, support.reload(seats.get(2)).getStatus());
    }

    @Test
    void testThatAnotherGroupCannotReleaseAHold() {
        diningSessionService.holdSeats(host, List.of(id(1)));
        CustomerPrincipal stranger = support.claim(seats.get(3), "stranger");

        assertThrows(SeatOwnershipException.class, () -> diningSessionService.releaseHold(stranger, id(1)));
        assertThrows(SeatStatusException.class, () -> diningSessionService.releaseHold(host, id(2)));
        assertEquals(SeatStatus.HELD, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatAGuestWithoutAPhoneIsConfirmedOntoAHeldSeatByName() {
        diningSessionService.holdSeats(host, List.of(id(1)));

        diningSessionService.confirmGuest(host, guest(1, " Grandma "));

        SessionSeat membership = support.membershipOf(id(1));
        assertEquals(SessionSeatStatus.OCCUPIED, membership.getStatus());
        assertEquals("Grandma", membership.getGuestName());
        assertFalse(membership.isHasDevice());
        assertNull(membership.getGuestPhone());
        assertNull(membership.getHeldUntil());
        assertEquals(SeatStatus.OCCUPIED, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatAGuestCannotBeConfirmedOntoAVacantSeat() {
        assertThrows(SeatStatusException.class, () -> diningSessionService.confirmGuest(host, guest(1, "Grandma")));
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatAGuestCannotBeConfirmedOnceTheHoldHasRunOut() {
        diningSessionService.holdSeats(host, List.of(id(1)));
        support.expireHold(id(1));
        assertThrows(SeatStatusException.class, () -> diningSessionService.confirmGuest(host, guest(1, "Grandma")));
    }

    @Test
    void testThatAGuestCannotBeConfirmedOntoAnotherGroupsSeat() {
        CustomerPrincipal stranger = support.claim(seats.get(3), "stranger");
        diningSessionService.holdSeats(stranger, List.of(id(2)));
        assertThrows(SeatOwnershipException.class, () -> diningSessionService.confirmGuest(host, guest(2, "Grandma")));
        assertThrows(SeatOwnershipException.class, () -> diningSessionService.confirmGuest(host, guest(3, "Grandma")));
    }

    @Test
    void testThatTheSessionViewShowsNamesAndOnlyTheCallersOwnPhone() {
        diningSessionService.holdSeats(host, List.of(id(1), id(2), id(3)));
        CustomerPrincipal friend = joinAs(1, "friend");
        diningSessionService.confirmGuest(host, guest(2, "Grandma"));
        support.expireHold(id(3));

        SessionViewResponse view = diningSessionService.getMySession(friend);

        assertEquals(host.getSessionId(), view.getSessionId());
        assertEquals(6, view.getJoinCode().length());
        assertEquals(id(1), view.getSeatId());
        assertEquals(table.getDisplayNumber(), view.getDisplayNumber());
        assertEquals(3, view.getSeats().size());
        SessionSeatView hostSeat = view.getSeats().get(0);
        SessionSeatView friendSeat = view.getSeats().get(1);
        SessionSeatView guestSeat = view.getSeats().get(2);
        assertEquals("host", hostSeat.getGuestName());
        assertNull(hostSeat.getGuestPhone());
        assertFalse(hostSeat.getMine());
        assertTrue(friendSeat.getMine());
        assertEquals("08149048149", friendSeat.getGuestPhone());
        assertEquals("Grandma", guestSeat.getGuestName());
        assertFalse(guestSeat.getHasDevice());
    }

    @Test
    void testThatACustomerWhoseSeatWasFreedIsToldTheSessionHasEnded() {
        seatService.releaseSeat(id(0));
        SessionEndedException exception = assertThrows(SessionEndedException.class, () -> diningSessionService.getMySession(host));
        assertEquals("Your session has ended", exception.getMessage());
    }

    private CustomerPrincipal joinAs(int index, String name) {
        com.scantoorder.scantoorder.dtos.request.JoinSeatRequest request = new com.scantoorder.scantoorder.dtos.request.JoinSeatRequest();
        request.setJoinCode(support.membershipOf(id(0)).getSession().getJoinCode());
        request.setCustomerName(name);
        request.setCustomerPhoneNumber("08149048149");
        seatService.joinSeat(id(index), request, null);
        return support.principalFor(id(index));
    }
}
