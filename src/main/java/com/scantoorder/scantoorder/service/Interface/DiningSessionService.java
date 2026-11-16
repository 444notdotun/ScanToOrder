package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.respond.CloseTableResponse;
import com.scantoorder.scantoorder.dtos.respond.LeaveSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.TableSessionsResponse;
import com.scantoorder.scantoorder.dtos.respond.SessionViewResponse;
import com.scantoorder.scantoorder.dtos.respond.SessionOrdersResponse;

import java.util.List;

public interface DiningSessionService {
    SessionViewResponse getMySession(CustomerPrincipal caller);
    SessionViewResponse holdSeats(CustomerPrincipal caller, List<String> seatIds);
    SessionViewResponse releaseHold(CustomerPrincipal caller, String seatId);
    SessionViewResponse confirmGuest(CustomerPrincipal caller, ConfirmGuestRequest request);
    SessionSeat seatToOrderFor(CustomerPrincipal caller, String targetSeatId);
    LeaveSessionResponse leave(CustomerPrincipal caller);
    CloseSessionResponse closeSession(String tenantId, String sessionId);
    // Closes without asking: the scheduler and the event ending use this.
    CloseTableResponse closeTable(String tenantId, String tableId);

    // From a waiter or manager: paid orders not yet delivered need confirm=true.
    CloseTableResponse closeTable(String tenantId, String tableId, boolean confirm);
    TableSessionsResponse getTableSessions(String tenantId, String tableId);
    ReleaseSeatResponse freeSeat(String tenantId, String seatId);
    int expireHolds();
    int closeAllActiveSessions();
    long getActiveSessionsCount(String tenantId);
    List<String> getSessionPaymentReferences(CustomerPrincipal caller);

    // The table's orders this guest may see, with what was paid.
    SessionOrdersResponse getSessionOrders(CustomerPrincipal caller);
}
