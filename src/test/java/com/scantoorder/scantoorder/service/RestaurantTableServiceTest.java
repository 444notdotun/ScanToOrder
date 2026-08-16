package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class RestaurantTableServiceTest {
    @Autowired
    private  RestaurantTableService restaurantTableService;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;
    private RestaurantTable table;
    private Seat seat;
    private Seat seat1;
    private Seat seat2;

    @AfterEach
    void tearDown() {
        seatRepo.deleteAll();
        tableRepo.deleteAll();
    }
    @BeforeEach
    void setUp() {
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
        String result =restaurantTableService.createTable( seatCapacity);
        assertEquals("Table created successfully", result);
        assertEquals(2, tableRepo.count());
    }

   @Test
    void testThatRestaurantTableStatusIsNotOccupied(){
        String tableId = table.getTableId();
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
       String tableId = table.getTableId();
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
       String tableId = table.getTableId();
       TableStatus tableStatus =   restaurantTableService.syncTableStatus(tableId);
       assertEquals(TableStatus.AVAILABLE, tableStatus);
   }
//
//   @Test






}