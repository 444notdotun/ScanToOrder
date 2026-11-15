package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.TableResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.config.DataSeeder;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RestaurantTableServiceTest {

    @Autowired
    private RestaurantTableService restaurantTableService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private SeatLedger seatLedger;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        support.clearFloor();
    }

    @Test
    void testThatATableGetsNumberedSeatPositions() {
        CreateRestaurantTableResponse result = restaurantTableService.createTable(4);
        assertEquals("Table created successfully", result.getMessage());
        List<Seat> seats = support.seatsOf(tableRepo.findById(result.getTableId()).orElseThrow());
        assertEquals(List.of(1, 2, 3, 4), seats.stream().map(Seat::getPosition).toList());
    }

    @Test
    void testThatATableCanHaveOneSeatAndUpToTwenty() {
        assertEquals(1, restaurantTableService.createTable(1).getCapacity());
        assertEquals(20, restaurantTableService.createTable(20).getCapacity());
    }

    @Test
    void testThatATableCannotHaveNoSeatsOrMoreThanTwenty() {
        assertThrows(InvalidRequestException.class, () -> restaurantTableService.createTable(0));
        assertThrows(InvalidRequestException.class, () -> restaurantTableService.createTable(21));
        assertEquals(0, tableRepo.count());
    }

    @Test
    void testThatTablesTakeTheNextFreeNumber() {
        assertEquals(1, restaurantTableService.createTable(2).getDisplayNumber());
        assertEquals(2, restaurantTableService.createTable(2).getDisplayNumber());
        assertEquals(5, restaurantTableService.createTable(new CreateTableRequest(2, 5)).getDisplayNumber());
        assertEquals(3, restaurantTableService.createTable(2).getDisplayNumber());
    }

    @Test
    void testThatATableNumberCannotBeUsedTwice() {
        restaurantTableService.createTable(new CreateTableRequest(2, 7));
        assertThrows(ConflictException.class, () -> restaurantTableService.createTable(new CreateTableRequest(4, 7)));
        assertEquals(1, tableRepo.count());
    }

    @Test
    void testThatTableStatusIsWorkedOutFromItsSeats() {
        RestaurantTable table = support.newTable(2);
        List<Seat> seats = support.seatsOf(table);
        assertEquals(TableStatus.AVAILABLE, restaurantTableService.syncTableStatus(table.getTableNumber()));

        CustomerPrincipal host = support.claim(seats.get(0), "host");
        assertEquals(TableStatus.AVAILABLE, restaurantTableService.syncTableStatus(table.getTableNumber()));

        DinningSession session = dinningSessionRepo.findById(host.getSessionId()).orElseThrow();
        seatLedger.hold(session, support.reload(seats.get(1)));
        assertEquals(TableStatus.OCCUPIED, restaurantTableService.syncTableStatus(table.getTableNumber()));
        TableResponse listed = restaurantTableService.viewAllTable().get(0);
        assertEquals(TableStatus.OCCUPIED, listed.getStatus());

        support.expireHold(seats.get(1).getSeatId());
        assertEquals(TableStatus.AVAILABLE, restaurantTableService.syncTableStatus(table.getTableNumber()));
    }

    @Test
    void testThatTheSeatMapShowsPositionsAndStatesAndTreatsAnExpiredHoldAsVacant() {
        RestaurantTable table = support.newTable(3);
        List<Seat> seats = support.seatsOf(table);
        CustomerPrincipal host = support.claim(seats.get(0), "host");
        DinningSession session = dinningSessionRepo.findById(host.getSessionId()).orElseThrow();
        seatLedger.hold(session, support.reload(seats.get(1)));
        seatLedger.hold(session, support.reload(seats.get(2)));
        support.expireHold(seats.get(2).getSeatId());

        ViewTableAndSeatAvailabilityResponse map = restaurantTableService.viewTableAndSeatAvailability(table.getTableNumber());

        assertEquals(table.getTableNumber(), map.getTableNumber());
        assertEquals(table.getDisplayNumber(), map.getDisplayNumber());
        assertEquals(3, map.getSeats().size());
        assertEquals(1, map.getSeats().get(0).getPosition());
        assertEquals(seats.get(0).getSeatId(), map.getSeats().get(0).getSeatId());
        assertEquals(SeatStatus.OCCUPIED, map.getSeats().get(0).getStatus());
        assertEquals(SeatStatus.HELD, map.getSeats().get(1).getStatus());
        assertEquals(SeatStatus.VACANT, map.getSeats().get(2).getStatus());
    }

    @Test
    void testThatATableWithGuestsCannotBeRemoved() {
        RestaurantTable table = support.newTable(2);
        support.claim(support.seatsOf(table).get(0), "guest");
        assertThrows(ConflictException.class, () -> restaurantTableService.deleteTable(table.getTableId()));
        assertEquals(1, restaurantTableService.viewAllTable().size());
    }

    @Test
    void testThatARemovedTableIsSwitchedOffAndItsNumberFreed() {
        RestaurantTable table = support.newTable(2);
        Seat seat = support.seatsOf(table).get(0);
        support.claim(seat, "guest");
        seatService.releaseSeat(seat.getSeatId());

        restaurantTableService.deleteTable(table.getTableId());

        assertTrue(restaurantTableService.viewAllTable().isEmpty());
        assertThrows(TableNotFoundException.class, () -> restaurantTableService.viewTableAndSeatAvailability(table.getTableNumber()));
        assertEquals(1, restaurantTableService.createTable(2).getDisplayNumber());
    }

    @Test
    void testThatTheSeederAddsFourTablesOfOneTwoFourAndSixSeats() {
        dataSeeder.run();
        assertEquals(List.of(1, 2, 4, 6), restaurantTableService.viewAllTable().stream().map(TableResponse::getCapacity).toList());
        assertEquals(List.of(1, 2, 3, 4), restaurantTableService.viewAllTable().stream().map(TableResponse::getDisplayNumber).toList());
    }

    @Test
    void testGetQrCodeReturnsValidPng() {
        RestaurantTable table = support.newTable(2);
        byte[] qrCodeBytes = restaurantTableService.getQrCode(table.getTableNumber());

        assertNotNull(qrCodeBytes);
        assertTrue(qrCodeBytes.length > 0);
        assertEquals((byte) -119, qrCodeBytes[0]);
        assertEquals((byte) 80, qrCodeBytes[1]);
        assertEquals((byte) 78, qrCodeBytes[2]);
        assertEquals((byte) 71, qrCodeBytes[3]);
    }
}
