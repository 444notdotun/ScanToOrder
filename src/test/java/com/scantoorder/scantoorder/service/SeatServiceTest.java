package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeatServiceTest {
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatRepo  seatRepo;
    private Seat seat;
    @AfterEach
    void tearDown() {
        seatRepo.deleteAll();
    }
    private UpdateSeatRequest updateSeatRequest;

@BeforeEach
void setUp() {
        seat = new Seat();
    seat.setStatus(SeatStatus.VACANT);
    Seat seat1 = new Seat();
    seat1.setStatus(SeatStatus.VACANT);
    seatRepo.save(seat);
    seatRepo.save(seat1);
    updateSeatRequest = new UpdateSeatRequest();
    updateSeatRequest.setSeatId(seat.getSeatId());
}

    @Test
    void testThatSeatStatusCanBeSeen(){
        String seatStatus = seatService.viewAllSeatStatus();
        assertNotNull(seatStatus);
        System.out.println(seatStatus);
    }

    @Test
    void testThatASeatCanBeOccupiedOrClaim(){
        assertEquals(SeatStatus.VACANT,seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState( "OCCUPIED");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
    }

    @Test
    void TestThatSeatCanBeHeld(){
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT,seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("HELD");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
    }

    @Test
    void testThatSeatCanBeHeldAndThenOccupied(){
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT,seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("HELD");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
    }

    @Test
    void teatThatSeatCanNotMoveFromOccupiedToHeld(){
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT,seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
        updateSeatRequest.setNewState("HELD");
        assertThrows(SeatStatusException.class,()-> seatService.updateSeat(updateSeatRequest));
    }

    @Test
    void testThatSeatCanBeOccupiedAndMovedToVacant(){
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT,seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("OCCUPIED");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
        updateSeatRequest.setNewState("VACANT");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
    }


    @Test
    void testThatSeatCanMoveFromHeldToVacant(){
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        assertEquals(SeatStatus.VACANT,seatRepo.findSeatBySeatId(seat.getSeatId()).get().getStatus());
        updateSeatRequest.setNewState("HELD");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
        updateSeatRequest.setNewState("VACANT");
        assertEquals("your"+seat.getSeatNumber()+" is updated",seatService.updateSeat(updateSeatRequest));
    }




}