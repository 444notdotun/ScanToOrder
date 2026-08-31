package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class RestaurantTableServiceTest {
    @Autowired
    private RestaurantTableService restaurantTableService;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;
    private RestaurantTable table;
    private Seat seat;
    private Seat seat1;
    private Seat seat2;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;

    @AfterEach
    void tearDown() {

    }
    @BeforeEach
    void setUp() {
        dinningSessionRepo.deleteAll();
        seatRepo.deleteAll();
        tableRepo.deleteAll();

         table = new RestaurantTable();
        table = tableRepo.save(table);
         seat = new Seat();
        seat.setTableId(table);
         seat1 = new Seat();
        seat1.setTableId(table);
         seat2 = new Seat();
        seat2.setTableId(table);

        seatRepo.save(seat);
        seatRepo.save(seat1);
        seatRepo.save(seat2);
    }

    @Test
    void testThatRestaurantTableCanBeCreated(){
        int seatCapacity=4;
        CreateRestaurantTableResponse result =restaurantTableService.createTable( seatCapacity);
        assertEquals("Table created successfully", result.getMessage());
        assertEquals(2, tableRepo.count());
    }

   @Test
    void testThatRestaurantTableStatusIsNotOccupied(){
        String tableId = table.getTableNumber();
        TableStatus tableStatus =   restaurantTableService.syncTableStatus(tableId);
        assertEquals(TableStatus.AVAILABLE, tableStatus);
   }

   @Test
    void testThatTableCanBeOccupied(){
        seat1.setStatus(SeatStatus.OCCUPIED);
        seat.setStatus(SeatStatus.OCCUPIED);
        seat2.setStatus(SeatStatus.OCCUPIED);
        seatRepo.save(seat);
        seatRepo.save(seat1);
        seatRepo.save(seat2);
       String tableId = table.getTableNumber();
       TableStatus tableStatus =   restaurantTableService.syncTableStatus(tableId);
       assertEquals(TableStatus.OCCUPIED, tableStatus);
   }
   @Test
    void testThatTableNeverOccupiedIfAllSeatIsNotOccupiedOrOnHeld(){
       seat1.setStatus(SeatStatus.OCCUPIED);
       seat.setStatus(SeatStatus.OCCUPIED);
       seat2.setStatus(SeatStatus.VACANT);
       seatRepo.save(seat);
       seatRepo.save(seat1);
       seatRepo.save(seat2);
       String tableId = table.getTableNumber();
       TableStatus tableStatus =   restaurantTableService.syncTableStatus(tableId);
       assertEquals(TableStatus.AVAILABLE, tableStatus);
   }


   @Test
    void testThatAllTablesCanBeSeen(){
        restaurantTableService.createTable(4);
        restaurantTableService.createTable(5);
        restaurantTableService.createTable(6);
        restaurantTableService.createTable(7);
        restaurantTableService.createTable(8);
        assertNotNull(restaurantTableService.viewAllTable());

        assertEquals(6, tableRepo.count());
   }

   @Test
    void testThatTableAvailabilityAndItsSeatCanBeSeen(){
       CreateRestaurantTableResponse response = restaurantTableService.createTable(4);
        ViewTableAndSeatAvailabilityResponse result = restaurantTableService.viewTableAndSeatAvailability(response.getTableNumber());
        assertNotNull(result);
        assertEquals(response.getTableNumber(), result.getTableNumber());

    }

//    @Test
//    void testThatMenuCanBeGenerated(){
//
//    }








    @Test
    void testGetQrCodeReturnsValidPng() {
        String tableNumber = table.getTableNumber();
        byte[] qrCodeBytes = restaurantTableService.getQrCode(tableNumber);

        assertNotNull(qrCodeBytes);
        assertTrue(qrCodeBytes.length > 0);

        // Assert valid PNG header bytes
        assertEquals((byte) -119, qrCodeBytes[0]);
        assertEquals((byte) 80, qrCodeBytes[1]);
        assertEquals((byte) 78, qrCodeBytes[2]);
        assertEquals((byte) 71, qrCodeBytes[3]);
    }
}