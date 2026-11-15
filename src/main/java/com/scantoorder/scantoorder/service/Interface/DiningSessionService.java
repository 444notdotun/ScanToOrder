package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;

import java.util.List;

public interface DiningSessionService {
    CloseSessionResponse closeSession(String sessionId);
    ReleaseSeatResponse freeSeat(String seatId);
    long getActiveSessionsCount();
    List<String> getSessionPaymentReferences(CustomerPrincipal caller);
}
