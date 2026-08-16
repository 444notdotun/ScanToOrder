package com.scantoorder.scantoorder.service;

public interface SeatService {

    String viewAllSeatStatus();

    boolean SeatTableSync(String tableId);

    String updateSeat(String update, String seatId);
}
