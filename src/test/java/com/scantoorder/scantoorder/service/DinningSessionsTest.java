package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.CreateSessionRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DinningSessionsTest {
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private TableService tableService;
    @Autowired
    private DinningSessions dinningSession;

    private String tableNumber;
    private CreateSessionRequest createSessionRequest;
    private String seatId;


    @BeforeEach
    void setup() {

        CreateRestaurantTableResponse createRestaurantTableResponse = tableService.createTable(4);
         createSessionRequest = new CreateSessionRequest();
        createSessionRequest.setCustomerEmail("adedortmahan@gmail.com");
        createSessionRequest.setCustomerName("adedotun");
        createSessionRequest.setTableNumber(createRestaurantTableResponse.getTableNumber());
        createSessionRequest.setCustomerPhone("08149048149");
        Optional<RestaurantTable> restaurantTable =tableRepo.findByTableNumber(createRestaurantTableResponse.getTableNumber());
        Optional<List<Seat>> seat = seatRepo.findSeatByTableId(restaurantTable.get());
        seatId=seat.get().get(0).getSeatId();
        tableNumber=seat.get().get(0).getTableId().getTableNumber();


    }


    @Test
    void testDinningSessionCanBeCreated(){
        String  createSessionResponse = dinningSession.createSession(seatId,tableNumber,createSessionRequest.getCustomerName(),createSessionRequest.getCustomerPhone(),createSessionRequest.getCustomerEmail());
        assertNotNull(dinningSessionRepo.findDinningSessionBySessionId(createSessionResponse));
        assertNotNull(createSessionResponse);
    }


}