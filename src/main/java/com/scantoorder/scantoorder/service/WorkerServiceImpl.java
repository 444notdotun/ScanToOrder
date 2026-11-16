package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.StaffResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import com.scantoorder.scantoorder.utils.TemporaryPassword;
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
    private TenantRepo tenantRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    @Override
    public StaffResponse createWorker(String tenantId, String name, String role, String handle) {
        WorkerRole workerRole = staffRole(role);
        Tenant tenant = tenantRepo.getReferenceById(tenantId);
        String cleanHandle = cleanHandle(handle);
        String username = tenant.getSlug() + "." + cleanHandle;
        if (workerRepo.existsByUsername(username)) {
            throw new ConflictException("The sign-in " + username + " is already taken. Choose another handle.");
        }
        String temporaryPassword = TemporaryPassword.generate();
        Worker worker = new Worker();
        worker.setTenant(tenant);
        worker.setFullName(name.trim());
        worker.setUsername(username);
        worker.setPassword(passwordEncoder.encode(temporaryPassword));
        worker.setMustChangePassword(true);
        worker.setRole(workerRole);
        worker = workerRepo.save(worker);
        return new StaffResponse(worker.getWorkerId(), worker.getUsername(), worker.getFullName(), workerRole.name(), temporaryPassword);
    }

    @Override
    public List<StaffResponse> getAllWorkers(String tenantId) {
        return workerRepo.findByTenant_TenantIdAndIsActiveTrue(tenantId).stream()
                .map(worker -> new StaffResponse(worker.getWorkerId(), worker.getUsername(), worker.getFullName(), worker.getRole().name(), null))
                .toList();
    }

    // The account is kept for the records but cannot sign in, and its tokens stop working at once.
    @Transactional
    @Override
    public void deleteWorker(String tenantId, String workerId) {
        Worker worker = workerRepo.findByWorkerIdAndTenant_TenantId(workerId, tenantId)
                .filter(Worker::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that team member"));
        if (worker.getRole() == WorkerRole.MANAGER) {
            throw new ConflictException("The manager's account cannot be removed.");
        }
        worker.setActive(false);
        workerRepo.save(worker);
    }

    // A manager exists only through approval of the business. This makes chefs and waiters.
    private WorkerRole staffRole(String role) {
        WorkerRole workerRole;
        try {
            workerRole = WorkerRole.valueOf(role == null ? "" : role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Choose a role: chef or waiter");
        }
        if (workerRole != WorkerRole.CHEF && workerRole != WorkerRole.WAITER) {
            throw new InvalidRequestException("A " + workerRole.name().toLowerCase().replace('_', ' ')
                    + " account cannot be created here. Choose chef or waiter.");
        }
        return workerRole;
    }

    private String cleanHandle(String handle) {
        String clean = handle == null ? "" : handle.trim().toLowerCase();
        if (!clean.matches("[a-z0-9]{2,20}")) {
            throw new InvalidRequestException("A handle is 2 to 20 letters or numbers, with no spaces.");
        }
        return clean;
    }
}
