package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// What a guest may see about an event before they type a code: what it is called and when the doors open.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventTablePreviewResponse {
    private String eventName;
    private String venue;
    private int tableNumber;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    // False before the doors open or after the event is over: a code will not work yet or any more.
    private boolean open;
    private boolean over;
}
