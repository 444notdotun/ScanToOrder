package com.scantoorder.scantoorder.dtos.request;

import lombok.Data;

@Data
public class CreateServiceCallRequest {
    private String requestType;
    private String note;
}
