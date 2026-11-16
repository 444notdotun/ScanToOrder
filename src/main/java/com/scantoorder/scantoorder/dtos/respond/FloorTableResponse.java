package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// A table of the restaurant, seat by seat, as it is right now.
@Data
public class FloorTableResponse {
    private String tableId;
    private String tableNumber;
    private Integer displayNumber;
    private int capacity;
    // How many separate parties are seated here.
    private int groups;
    private List<Seat> seats = new ArrayList<>();

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Seat {
        private String seatId;
        private int position;
        // VACANT, HELD or OCCUPIED.
        private String status;
        private Long secondsLeft;
        private String guestFirstName;
    }
}
