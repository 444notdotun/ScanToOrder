package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionsImplementation implements DinningSessions {
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private TableRepo tableRepo;
    @Override
    public String createSession(String seatId, String tableNumber, String customerName, String customerPhone, String customerEmail) {
        Seat seat = seatRepo.findSeatBySeatId(seatId).orElseThrow(() -> new SeatNotFoundException("Seat not found"));
        RestaurantTable table = tableRepo.findByTableNumber(tableNumber).orElseThrow(() -> new TableNotFoundException("Table not found"));
        DinningSession dinningSession = new DinningSession();
        dinningSession.getSeats().add(seat);
        dinningSession.setCustomerName(customerName);
        dinningSession.setCustomerPhone(customerPhone);
        dinningSession.setCustomerEmail(customerEmail);
        dinningSession.setTableId(table);
        dinningSessionRepo.save(dinningSession);
        return dinningSession.getSessionId();
    }


}
