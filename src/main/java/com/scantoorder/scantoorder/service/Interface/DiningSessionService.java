package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.CreateSessionRequest;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateSessionResponse;

public interface DiningSessionService {
    CreateSessionResponse createSession(CreateSessionRequest request);
    CloseSessionResponse closeSession(String sessionId);
    CloseSessionResponse closeSessionByTableId(String tableId);
    CloseSessionResponse closeSessionBySeatId(String seatId);
    long getActiveSessionsCount();
    java.util.List<String> getSessionPaymentReferences(String sessionId);
    String createSession(String seatId, String tableNumber, String customerName, String customerPhone, String customerEmail);
}
