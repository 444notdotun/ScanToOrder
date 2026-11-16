package com.scantoorder.scantoorder.dtos.respond;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// The manager's room right now.
@Data
public class FloorResponse {
    private List<FloorTableResponse> tables = new ArrayList<>();
    private long paid;
    private long cooking;
    private long ready;
    private long needVisit;
    private long openCalls;
    private long takenCalls;
    private List<WaiterCallResponse> calls = new ArrayList<>();
    private long openRefunds;
    private long overdueRefunds;
    private List<String> soldOut = new ArrayList<>();
}
