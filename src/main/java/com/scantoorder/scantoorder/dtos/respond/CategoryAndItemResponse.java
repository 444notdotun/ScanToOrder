package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryAndItemResponse {
    private String CategoryName;
    private List<ItemResponse> itemResponse;

    public CategoryAndItemResponse(){
        itemResponse=new ArrayList<>();
    }
}
