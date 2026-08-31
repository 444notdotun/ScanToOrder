package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.Worker;
import java.util.List;

public interface WorkerService {
    Worker createWorker(String name, String role, String username);
    List<Worker> getAllWorkers();
    void deleteWorker(String workerId);
}
