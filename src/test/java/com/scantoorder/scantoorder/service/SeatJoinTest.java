package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.exception.SeatOwnershipException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SeatTakenException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeatJoinTest {
    @Autowired
    private SeatService seatService;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private SeatingTestSupport support;

    private List<Seat> seats;
    private CustomerPrincipal host;
    private String joinCode;

    @BeforeEach
    void setUp() {
        support.clearFloor();
        RestaurantTable table = support.newTable(4);
        seats = support.seatsOf(table);
        host = support.claim(seats.get(0), "host");
        joinCode = diningSessionService.holdSeats(host, List.of(id(1))).getJoinCode();
    }

    private String id(int index) {
        return seats.get(index).getSeatId();
    }

    private JoinSeatRequest join(String code) {
        JoinSeatRequest request = new JoinSeatRequest();
        request.setJoinCode(code);
        request.setCustomerName("friend");
        request.setCustomerPhoneNumber("08012345678");
        return request;
    }

    @Test
    void testThatAFriendJoinsAHeldSeatWithTheCodeAndGetsTheirOwnToken() {
        SeatClaimedResponse response = seatService.joinSeat(id(1), join(" " + joinCode.toLowerCase() + " "), null);

        assertNotNull(response.getToken());
        assertEquals(host.getSessionId(), response.getSessionId());
        SessionSeat membership = support.membershipOf(id(1));
        assertEquals(SessionSeatStatus.OCCUPIED, membership.getStatus());
        assertEquals("friend", membership.getGuestName());
        assertEquals("08012345678", membership.getGuestPhone());
        assertTrue(membership.isHasDevice());
        assertNull(membership.getHeldUntil());
        assertEquals(SeatStatus.OCCUPIED, support.reload(seats.get(1)).getStatus());
    }

    @Test
    void testThatAWrongCodeIsRejectedAndCanBeTriedAgain() {
        assertThrows(SeatOwnershipException.class, () -> seatService.joinSeat(id(1), join("WRONG1"), null));
        assertThrows(SeatOwnershipException.class, () -> seatService.joinSeat(id(1), join("WRONG2"), null));
        assertEquals(SeatStatus.HELD, support.reload(seats.get(1)).getStatus());
        assertNotNull(seatService.joinSeat(id(1), join(joinCode), null).getToken());
    }

    @Test
    void testThatACodeFromAnotherGroupAtTheTableIsAWrongCode() {
        CustomerPrincipal stranger = support.claim(seats.get(3), "stranger");
        String strangersCode = diningSessionService.getMySession(stranger).getJoinCode();
        assertThrows(SeatOwnershipException.class, () -> seatService.joinSeat(id(1), join(strangersCode), null));
    }

    @Test
    void testThatTheCodeNoLongerAppliesOnceTheHoldHasRunOut() {
        support.expireHold(id(1));
        assertThrows(SeatStatusException.class, () -> seatService.joinSeat(id(1), join(joinCode), null));
        assertNotNull(seatService.claimSeat(support.claimRequest(seats.get(1), "friend"), null).getToken());
    }

    @Test
    void testThatTheCodeDoesNotOpenAVacantSeat() {
        assertThrows(SeatStatusException.class, () -> seatService.joinSeat(id(2), join(joinCode), null));
        assertEquals(SeatStatus.VACANT, support.reload(seats.get(2)).getStatus());
    }

    @Test
    void testThatASeatThatBecameOccupiedCannotBeJoined() {
        seatService.joinSeat(id(1), join(joinCode), null);
        assertThrows(SeatTakenException.class, () -> seatService.joinSeat(id(1), join(joinCode), null));
    }

    @Test
    void testThatAfterTheGroupsSessionClosesTheFriendClaimsNormally() {
        seatService.releaseSeat(support.tenantId(), id(0));
        assertThrows(SeatStatusException.class, () -> seatService.joinSeat(id(1), join(joinCode), null));
        SeatClaimedResponse claimed = seatService.claimSeat(support.claimRequest(seats.get(1), "friend"), null);
        assertNotEquals(host.getSessionId(), claimed.getSessionId());
    }

    @Test
    void testThatAGuestSeatedAtAnotherTableCannotJoin() {
        CustomerPrincipal elsewhere = support.claim(support.seatsOf(support.newTable(1)).get(0), "elsewhere");
        assertThrows(SeatStatusException.class, () -> seatService.joinSeat(id(1), join(joinCode), elsewhere));
        assertEquals(SeatStatus.HELD, support.reload(seats.get(1)).getStatus());
    }
}
