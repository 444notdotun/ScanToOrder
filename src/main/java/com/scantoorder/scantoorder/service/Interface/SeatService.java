package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.UpdateSeatResponse;

import java.util.List;

public interface SeatService {
    List<SeatStatusResponse> viewAllSeatStatus(String tenantId);
    UpdateSeatResponse updateSeat(String tenantId, UpdateSeatRequest updateSeatRequest);
    SeatClaimedResponse claimSeat(ClaimSeatRequest claimSeatRequest, CustomerPrincipal caller);
    SeatClaimedResponse joinSeat(String seatId, JoinSeatRequest joinSeatRequest, CustomerPrincipal caller);
    ReleaseSeatResponse releaseSeat(String tenantId, String seatId);
}
