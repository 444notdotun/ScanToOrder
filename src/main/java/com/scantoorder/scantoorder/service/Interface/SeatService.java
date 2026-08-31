package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.UpdateSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import java.util.List;

public interface SeatService {
    List<SeatStatusResponse> viewAllSeatStatus();
    boolean SeatTableSync(String tableId);
    UpdateSeatResponse updateSeat(UpdateSeatRequest updateSeatRequest);
    SeatClaimedResponse claimSeat(ClaimSeatRequest claimSeatRequest);
    ReleaseSeatResponse releaseSeat(String seatId);
}
