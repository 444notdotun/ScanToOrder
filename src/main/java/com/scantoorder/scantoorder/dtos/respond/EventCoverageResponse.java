package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// Has everyone eaten? Each guest is in exactly one state, so the counts add up to the list.
@Data
public class EventCoverageResponse {
    private int onList;
    private int notArrived;
    private int seatedNotOrdered;
    private int waitingForPlate;
    private int eaten;
    private List<Guest> guests = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Guest {
        private String guestId;
        private String name;
        // NOT_ARRIVED, SEATED_NOT_ORDERED, WAITING_FOR_PLATE or EATEN.
        private String state;
        private Integer tableNumber;
        private Integer seatPosition;
        private int platesUsed;
        private Integer plateLimit;
    }
}
