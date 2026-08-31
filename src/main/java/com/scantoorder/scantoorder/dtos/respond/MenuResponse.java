package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuResponse {
    private List<CategoryAndItemResponse> categoryAndItemResponse;
    public MenuResponse(){
        categoryAndItemResponse=new ArrayList<>();
    }
}
