package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.Event;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventResponse {
    private String id;
    private String name;
    private String venue;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String seatingMode;
    private Integer platesPerGuest;
    private int expectedGuests;
    private int paidGuests;
    private long guestsOnList;
    private String status;

    public static EventResponse of(Event event, long guestsOnList) {
        EventResponse response = new EventResponse();
        response.setId(event.getId());
        response.setName(event.getName());
        response.setVenue(event.getVenue());
        response.setStartsAt(event.getStartsAt());
        response.setEndsAt(event.getEndsAt());
        response.setSeatingMode(event.getSeatingMode().name());
        response.setPlatesPerGuest(event.getPlatesPerGuest());
        response.setExpectedGuests(event.getExpectedGuests());
        response.setPaidGuests(event.getPaidGuests());
        response.setGuestsOnList(guestsOnList);
        response.setStatus(event.getStatus().name());
        return response;
    }
}
