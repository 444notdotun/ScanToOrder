package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ItemResponse {
    private String ItemName;
    private String ItemDescription;
    private String ItemPrice;

}
