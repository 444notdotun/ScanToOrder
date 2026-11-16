package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.EventPlate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventPlateResponse {
    private String id;
    private String name;
    private String description;
    private boolean available;

    public static EventPlateResponse of(EventPlate plate) {
        return new EventPlateResponse(plate.getId(), plate.getName(), plate.getDescription(), plate.isAvailable());
    }
}
