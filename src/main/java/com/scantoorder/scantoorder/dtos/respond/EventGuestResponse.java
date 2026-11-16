package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.EventGuest;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventGuestResponse {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String code;
    private Integer plateLimit;
    private int platesUsed;
    private Integer tableNumber;
    private Integer seatPosition;
    private LocalDateTime seatedAt;

    public static EventGuestResponse of(EventGuest guest) {
        EventGuestResponse response = new EventGuestResponse();
        response.setId(guest.getId());
        response.setName(guest.getName());
        response.setPhone(guest.getPhone());
        response.setEmail(guest.getEmail());
        response.setCode(guest.getCode());
        response.setPlateLimit(guest.getPlateLimit());
        response.setPlatesUsed(guest.getPlatesUsed());
        if (guest.getAssignedSeat() != null) {
            response.setSeatPosition(guest.getAssignedSeat().getPosition());
            response.setTableNumber(guest.getAssignedSeat().getTableId().getDisplayNumber());
        }
        response.setSeatedAt(guest.getSeatedAt());
        return response;
    }
}
