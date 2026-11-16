package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// One ticket on the pass or the waiter's list.
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceOrderResponse {
    private String orderId;
    private String orderNumber;
    private Integer tableNumber;
    private Integer seatPosition;
    private String guestName;
    // Items as "2 x Jollof rice", or the plate at an event.
    private List<String> items = new ArrayList<>();
    private String note;
    private String step;
    private long minutesWaiting;
    // For the waiter: "Ready for 25 minutes. It is getting cold." from 20 minutes on.
    private String flag;
    private String unavailableReason;
}
