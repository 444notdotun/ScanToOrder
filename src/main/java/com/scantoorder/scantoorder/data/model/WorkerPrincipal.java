package com.scantoorder.scantoorder.data.model;

import lombok.Data;

@Data
public class WorkerPrincipal {
    private final String workerId;
    private final String role;

    public WorkerPrincipal(String workerId, String role) {
        this.workerId = workerId;
        this.role = role;
    }

}
