package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ApiResponse<T> {
    private String status;
   private String message;
   private T data;
    public ApiResponse(T data) {
        this.data = data;
        this.status = "success";
    }
    public ApiResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

}
