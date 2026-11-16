package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.StaffResponse;

import java.util.List;

public interface WorkerService {
    StaffResponse createWorker(String tenantId, String name, String role, String handle);

    List<StaffResponse> getAllWorkers(String tenantId);

    void deleteWorker(String tenantId, String workerId);
}
