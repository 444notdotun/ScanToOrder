package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkerServiceImpl implements WorkerService {

    @Autowired
    private WorkerRepo workerRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    @Override
    public Worker createWorker(String name, String role, String username) {
        if (workerRepo.existsByUsername(username)) {
            throw new RuntimeException("Worker with username @" + username + " already exists.");
        }

        Worker worker = new Worker();
        worker.setFullName(name);
        worker.setUsername(username);
        // Default password for new staff profile
        worker.setPassword(passwordEncoder.encode("Password11"));
        worker.setRole(WorkerRole.valueOf(role.toUpperCase()));
        
        return workerRepo.save(worker);
    }

    @Override
    public List<Worker> getAllWorkers() {
        return workerRepo.findAll();
    }

    @Transactional
    @Override
    public void deleteWorker(String workerId) {
        if (!workerRepo.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
        workerRepo.deleteById(workerId);
    }
}
