package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SeatingTestSupport {
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ServiceCallRepo serviceCallRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private SeatService seatService;

    public void clearFloor() {
        paymentRepo.deleteAll();
        orderItemRepo.deleteAll();
        orderRepo.deleteAll();
        serviceCallRepo.deleteAll();
        sessionSeatRepo.deleteAll();
        dinningSessionRepo.deleteAll();
        seatRepo.deleteAll();
        tableRepo.deleteAll();
    }

    public RestaurantTable newTable(int capacity) {
        String tableId = tableService.createTable(capacity).getTableId();
        return tableRepo.findById(tableId).orElseThrow();
    }

    public List<Seat> seatsOf(RestaurantTable table) {
        return seatRepo.findByTableIdOrderByPositionAsc(table);
    }

    public ClaimSeatRequest claimRequest(Seat seat, String name) {
        ClaimSeatRequest request = new ClaimSeatRequest();
        request.setSeatId(seat.getSeatId());
        request.setTableId(seat.getTableId().getTableNumber());
        request.setCustomerName(name);
        request.setCustomerPhoneNumber("08149048149");
        return request;
    }

    public CustomerPrincipal claim(Seat seat, String name) {
        SeatClaimedResponse response = seatService.claimSeat(claimRequest(seat, name), null);
        return principalFor(response.getSeatId());
    }

    public CustomerPrincipal principalFor(String seatId) {
        SessionSeat membership = membershipOf(seatId);
        return new CustomerPrincipal(membership.getSession().getSessionId(), seatId,
                membership.getSeat().getTableId().getTableId(), membership.getId());
    }

    public SessionSeat membershipOf(String seatId) {
        return sessionSeatRepo.findBySeatAndStatusIn(seatId, SeatLedger.ACTIVE).orElseThrow();
    }

    public void expireHold(String seatId) {
        SessionSeat membership = membershipOf(seatId);
        membership.setHeldUntil(LocalDateTime.now().minusSeconds(5));
        sessionSeatRepo.save(membership);
    }

    public Seat reload(Seat seat) {
        return seatRepo.findSeatBySeatId(seat.getSeatId()).orElseThrow();
    }
}
