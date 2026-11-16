package com.scantoorder.scantoorder.dtos.request;

import com.scantoorder.scantoorder.data.model.EventSeatingMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

// Used to create and to edit an event.
@Data
public class EventRequest {
    @NotBlank(message = "Give the event a name")
    @Size(max = 120, message = "That name is too long")
    private String name;
    @Size(max = 160, message = "That venue is too long")
    private String venue;
    @NotNull(message = "Say when the doors open")
    private LocalDateTime startsAt;
    @NotNull(message = "Say when the event ends")
    private LocalDateTime endsAt;
    @NotNull(message = "Choose assigned or free seating")
    private EventSeatingMode seatingMode;
    // Plates each guest may have. Leave out for as many as they like.
    @Min(value = 1, message = "A guest needs at least one plate")
    private Integer platesPerGuest;
    @Min(value = 1, message = "Expect at least one guest")
    private int expectedGuests;
}
