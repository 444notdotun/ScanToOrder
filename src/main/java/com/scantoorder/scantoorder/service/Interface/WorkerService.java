package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.StaffResponse;

import java.util.List;

public interface WorkerService {
    StaffResponse createWorker(String tenantId, String name, String role, String handle);

    List<StaffResponse> getAllWorkers(String tenantId);

    void deleteWorker(String tenantId, String workerId);

    // A new temporary password for a chef or waiter of this business, shown once. Their old sign-ins stop working.
    StaffResponse resetPassword(String tenantId, String workerId);
}
