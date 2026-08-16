package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

@BeforeEach
void setUp() {
        seat = new Seat();
    seat.setStatus(SeatStatus.VACANT);
    Seat seat1 = new Seat();
    seat1.setStatus(SeatStatus.VACANT);
    seatRepo.save(seat);
    seatRepo.save(seat1);
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
        String update = "OCCUPIED";
        assertEquals("your"+seat.getSeatNumber()+" is claimed",seatService.updateSeat(update,seat.getSeatId()));


    }




}