package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;

public interface SeatService {

    String viewAllSeatStatus();

    boolean SeatTableSync(String tableId);

    String updateSeat(UpdateSeatRequest updateSeatRequest);

    SeatClaimedResponse claimSeat(ClaimSeatRequest claimSeatRequest);
}
