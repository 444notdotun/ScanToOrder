package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;

public interface SeatService {

    String viewAllSeatStatus();

    boolean SeatTableSync(String tableId);

    String updateSeat(UpdateSeatRequest updateSeatRequest);
}
