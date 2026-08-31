package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatStatusResponse;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SeatServiceTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private TableRepo tableRepo;

    @Autowired
    private SeatRepo seatRepo;

    @Autowired
    private TableService tableService;

    @Autowired
    private DinningSessionRepo dinningSessionRepo;

    private Seat seat;
    private UpdateSeatRequest updateSeatRequest;
    private ClaimSeatRequest claimSeatRequest;

    @BeforeEach
    void setUp() {
        dinningSessionRepo.deleteAll();
        seatRepo.deleteAll();
        tableRepo.deleteAll();
        RestaurantTable table = new RestaurantTable();
        table.setCapacity(4);
        tableRepo.save(table);
        seat = new Seat();
        seat.setStatus(SeatStatus.VACANT);
        Seat seat1 = new Seat();
        seat.setTableId(table);
        seat1.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        seatRepo.save(seat1);
        updateSeatRequest = new UpdateSeatRequest();
        updateSeatRequest.setSeatId(seat.getSeatNumber());
        claimSeatRequest = new ClaimSeatRequest();
        claimSeatRequest.setSeatId(seat.getSeatNumber());
        claimSeatRequest.setTableId(seat.getTableId().getTableNumber());
        claimSeatRequest.setCustomerName("adedotun");
        claimSeatRequest.setCustomerPhoneNumber("08149048149");
        claimSeatRequest.setCustomerEmail("adedortmahan@gmail.com");
    }

    @Test
    void testThatSeatStatusCanBeSeen() {
        List<SeatStatusResponse> seatStatus = seatService.viewAllSeatStatus();
        assertNotNull(seatStatus);
        System.out.println(seatStatus);
    }

    @Test
    void testThatASeatCanBeOccupiedOrClaim() {
        assertEquals(SeatStatus.VACANT, seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
    }

    @Test
    void TestThatSeatCanBeHeld() {
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT, seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("HELD");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
    }

    @Test
    void testThatSeatCanBeHeldAndThenOccupied() {
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT, seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("HELD");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
    }

    @Test
    void teatThatSeatCanNotMoveFromOccupiedToHeld() {
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT, seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
        updateSeatRequest.setNewState("HELD");
        assertThrows(SeatStatusException.class, () -> seatService.updateSeat(updateSeatRequest));
    }

    @Test
    void testThatSeatCanBeOccupiedAndMovedToVacant() {
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT, seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
        updateSeatRequest.setNewState("VACANT");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
    }

    @Test
    void testThatSeatCanMoveFromHeldToVacant() {
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT, seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("HELD");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
        updateSeatRequest.setNewState("VACANT");
        assertEquals("your" + seat.getSeatNumber() + " is updated", seatService.updateSeat(updateSeatRequest).getMessage());
    }

    @Test
    void testThatSeatCanBeClaimed() {
        SeatClaimedResponse seatClaimedResponse = seatService.claimSeat(claimSeatRequest);
        assertEquals("Seat claimed successfully", seatClaimedResponse.getMessage());
        System.out.println(seatClaimedResponse);
        assertNotNull(seatClaimedResponse.getToken());
    }
}