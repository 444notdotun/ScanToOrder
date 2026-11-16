package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.EventRequest;
import com.scantoorder.scantoorder.dtos.respond.EventPriceResponse;
import com.scantoorder.scantoorder.dtos.respond.EventResponse;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;

import java.util.List;

/**
 * The planner's events: setting them up, paying for guests, and ending them.
 */
public interface EventService {
    EventResponse create(String tenantId, EventRequest request);

    EventResponse update(String tenantId, String eventId, EventRequest request);

    EventResponse get(String tenantId, String eventId);

    List<EventResponse> list(String tenantId);

    // The planner ends it early; the scheduler ends it at its end time.
    EventResponse end(String tenantId, String eventId);

    EventPriceResponse price(String tenantId, String eventId, int guests);

    InitializePaymentResponse startPayment(String tenantId, String eventId, int guests, String receiptEmail);

    default InitializePaymentResponse startPayment(String tenantId, String eventId, int guests) {
        return startPayment(tenantId, eventId, guests, null);
    }

    // Ends every event past its end time and closes its sessions. Returns how many it ended.
    int endFinishedEvents();
}
