package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ItemResponse {
    private String ItemName;
    private String ItemDescription;
    private String ItemPrice;
    // Only when the item has a photo.
    private String imageUrl;
    // Only when the restaurant has enough real history for this dish; never a made-up figure.
    private Integer readyInMinutes;

}
