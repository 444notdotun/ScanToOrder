package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// What a seated guest sees: the event, their own plates so far, and the plates to tap.
@Data
public class MyEventResponse {
    private String eventName;
    private String venue;
    private LocalDateTime endsAt;
    private String guestName;
    private int platesUsed;
    private Integer plateLimit;
    // The plate on its way, if there is one.
    private String platePending;
    private String note = "Nothing to pay. Tap a plate and it goes straight to the kitchen.";
    private List<EventPlateResponse> plates = new ArrayList<>();
}
