package com.scantoorder.scantoorder.dtos.request;

import lombok.Data;

@Data
public class UpdateSeatRequest {
    String newState;
    String seatId;
}
