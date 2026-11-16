package com.scantoorder.scantoorder.data.model;

import lombok.Data;

@Data
public class WorkerPrincipal {
    private final String workerId;
    private final String role;
    private final String tenantId;

    public WorkerPrincipal(String workerId, String role, String tenantId) {
        this.workerId = workerId;
        this.role = role;
        this.tenantId = tenantId;
    }
}
